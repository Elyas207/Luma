package me.knighthat.utils

import app.kreate.android.BuildConfig

object Repository {

    const val GITHUB = "https://github.com"
    const val GITHUB_API = "https://api.github.com"

    const val OWNER = "knighthat"

    /**
     * Upstream repository name.
     *
     * Pinned rather than derived from `BuildConfig.APP_NAME`. It used to be built from the app
     * name, so renaming the app to Luma silently repointed every "report an issue", "discussions"
     * and update-check URL at `knighthat/Luma`, which does not exist. The upstream project is
     * still called Kreate; the product name and the repository name are simply different things.
     */
    const val REPO = "$OWNER/Kreate"
    const val REPO_URL = "$GITHUB/$REPO"

    const val LATEST_TAG_URL = "$REPO/releases/latest"

    const val ISSUE_TEMPLATE_PATH = "/issues"
    const val FEATURE_REQUEST_TEMPLATE_PATH = "/issues/new?assignees=&labels=feature_request&template=feature_request.yaml"
}