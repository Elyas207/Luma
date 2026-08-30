package app.kreate.android.service.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The behaviours the personalisation layer is judged on, as deterministic scenarios over synthetic
 * histories. These are the cases from the testing prompt's failure catalogue — the ones where a
 * recommender goes wrong in a way the user can feel.
 *
 * Each one asserts what the *model concluded*, not that some function was called.
 */
class PersonalisationScenarioTest {

    private val cat = Archetypes.catalogue

    @Test
    fun `a brand new user produces no confident preference in any direction`() {
        val log = Archetypes.brandNew()
        val r = ReplayHarness.replay( log.events(), cat, log.nowMillis() )

        assertTrue( r.model.all().isEmpty() )
        assertEquals( 0.0, r.creatorEffect( "Mishary" ), 1e-9 )
    }

    @Test
    fun `someone who only listens to recitation is never taught to prefer nasheeds`() {
        val log = Archetypes.quranOnly()
        val r = ReplayHarness.replay( log.events(), cat, log.nowMillis() )

        assertTrue( "recitation should be a positive", r.classEffect( ContentClass.QURAN ) > 0.2 )
        assertEquals( "nothing should have been learned about nasheeds",
            0.0, r.classEffect( ContentClass.NASHEED ), 1e-9 )
    }

    @Test
    fun `a loved creator survives three skips of their new uploads`() {
        // The failure this guards against: a favourite quietly disappearing because their recent
        // uploads did not land. Downrank the items, keep the creator.
        val log = Archetypes.lovedThenSkipped()
        val r = ReplayHarness.replay( log.events(), cat, log.nowMillis() )

        assertTrue( "the creator must remain positive, was ${r.creatorEffect( "Siedd" )}",
            r.creatorEffect( "Siedd" ) > 0.15 )
        assertTrue( "the skipped item itself should be down, was ${r.itemEffect( "n2" )}",
            r.itemEffect( "n2" ) < 0.0 )
    }

    @Test
    fun `a run of skips is read as a mood, so it damages the profile far less`() {
        val log = Archetypes.chaoticSkipper()

        val takenAtFaceValue = ReplayHarness.replay( log.events(), cat, log.nowMillis() )
        val withCircuitBreaker = ReplayHarness.replay(
            log.events(), cat, log.nowMillis(), skipStreakFrom = 3
        )

        val naive = takenAtFaceValue.creatorEffect( "Siedd" )
        val guarded = withCircuitBreaker.creatorEffect( "Siedd" )

        assertTrue( "both should be negative", naive < 0 && guarded < 0 )
        assertTrue( "a bad evening must not count as much as a real dislike: $guarded vs $naive",
            guarded > naive )
    }

    @Test
    fun `skips on recitation barely move the creator, unlike skips on a nasheed`() {
        // Two identical histories, differing only in content class.
        val qari = TestItem( "x1", "SomeReciter", ContentClass.QURAN, 2_000_000 )
        val singer = TestItem( "x2", "SomeSinger", ContentClass.NASHEED, 200_000 )

        val a = SyntheticLog().apply { repeat( 5 ) { skipped( qari ); idle( 0.3 ) } }
        val b = SyntheticLog().apply { repeat( 5 ) { skipped( singer ); idle( 0.3 ) } }

        val ra = ReplayHarness.replay( a.events(), listOf( qari ), a.nowMillis() )
        val rb = ReplayHarness.replay( b.events(), listOf( singer ), b.nowMillis() )

        val quranHit = ra.creatorEffect( "SomeReciter" )
        val nasheedHit = rb.creatorEffect( "SomeSinger" )

        assertTrue( "both are negative", quranHit < 0 && nasheedHit < 0 )
        assertTrue( "recitation must be far more protected: $quranHit vs $nasheedHit",
            quranHit > nasheedHit * 0.6 )
    }

    @Test
    fun `a car session with no interaction does not produce a strong positive`() {
        // Not skipping while driving is cheap; it is not endorsement. This is the single most
        // over-trusted signal in car listening.
        val log = SyntheticLog().apply {
            repeat( 8 ) { completed( Archetypes.nasheedA, Provenance.AUTOPLAY ); idle( 0.05 ) }
        }

        val phone = ReplayHarness.replay( log.events(), cat, log.nowMillis(), isCarMode = false )
        val car = ReplayHarness.replay( log.events(), cat, log.nowMillis(), isCarMode = true )

        assertTrue( "car listening must teach much less: ${car.creatorEffect( "Siedd" )} vs ${phone.creatorEffect( "Siedd" )}",
            car.creatorEffect( "Siedd" ) < phone.creatorEffect( "Siedd" ) * 0.6 )
    }

    @Test
    fun `learning from autoplay alone stays weaker than learning from real choices`() {
        // The self-reinforcement failure: if the app's own picks counted like a user's, the
        // profile would collapse onto whatever the ranker liked first.
        val chosen = SyntheticLog().apply {
            repeat( 6 ) { completed( Archetypes.nasheedA, Provenance.SEARCH ); idle( 0.3 ) }
        }
        val served = SyntheticLog().apply {
            repeat( 6 ) { completed( Archetypes.nasheedA, Provenance.AUTOPLAY ); idle( 0.3 ) }
        }

        val rChosen = ReplayHarness.replay( chosen.events(), cat, chosen.nowMillis() )
        val rServed = ReplayHarness.replay( served.events(), cat, served.nowMillis() )

        assertTrue( rServed.creatorEffect( "Siedd" ) < rChosen.creatorEffect( "Siedd" ) )
    }

    @Test
    fun `a preference fades if it is not reinforced`() {
        val log = SyntheticLog().apply { repeat( 8 ) { completed( Archetypes.nasheedA ); idle( 0.2 ) } }
        val events = log.events()

        val fresh = ReplayHarness.replay( events, cat, log.nowMillis() )
        val muchLater = ReplayHarness.replay(
            events, cat, log.nowMillis() + 400L * 86_400_000   // over a year
        )

        assertTrue( fresh.creatorEffect( "Siedd" ) > 0.2 )
        assertTrue( "an unreinforced preference must decay: ${muchLater.creatorEffect( "Siedd" )}",
            muchLater.creatorEffect( "Siedd" ) < fresh.creatorEffect( "Siedd" ) * 0.5 )
    }

    @Test
    fun `the same log always replays to the same model`() {
        // Determinism is what makes the harness useful for tuning: a change in the output has to
        // be attributable to a change in the weights, not to run-to-run variation.
        val log = Archetypes.lovedThenSkipped()
        val a = ReplayHarness.replay( log.events(), cat, log.nowMillis() )
        val b = ReplayHarness.replay( log.events(), cat, log.nowMillis() )

        assertEquals( a.creatorEffect( "Siedd" ), b.creatorEffect( "Siedd" ), 1e-12 )
        assertEquals( a.itemEffect( "n2" ), b.itemEffect( "n2" ), 1e-12 )
    }

    @Test
    fun `an explicit favourite outweighs a single skip of the same thing`() {
        val log = SyntheticLog().apply {
            favourited( Archetypes.nasheedA )
            skipped( Archetypes.nasheedA )
        }
        val r = ReplayHarness.replay( log.events(), cat, log.nowMillis() )
        assertTrue( "an explicit signal should dominate one skip, was ${r.itemEffect( "n1" )}",
            r.itemEffect( "n1" ) > 0 )
    }

    @Test
    fun `a two second skip teaches nothing at all`() {
        val log = SyntheticLog().apply { repeat( 5 ) { skipped( Archetypes.nasheedA, atMs = 1_500 ) } }
        val r = ReplayHarness.replay( log.events(), cat, log.nowMillis() )

        assertEquals( "flicking past something is navigation, not judgement",
            0.0, r.creatorEffect( "Siedd" ), 1e-9 )
    }
}
