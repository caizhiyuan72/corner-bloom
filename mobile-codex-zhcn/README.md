# Mobile Codex zh-CN cloud build

Pinned upstream: `nokryong/mobile-codex` tag `v0.3.9` / commit `b6c12eb65fd29a38b0c2825bf12a7aca64736c5a`.

This experimental build lives on a separate branch to avoid touching corner-bloom main. It produces a debug-signed parallel-installable APK with application ID `dev.mobilecodex.app.zhcn` if GitHub Actions is enabled.

Workflow: `.github/workflows/mobile-codex-zhcn.yml`.

Download generated test APK in GitHub Actions run artifacts; do not uninstall the original app to try this build.

Sources and notices remain bundled from upstream GPL-3.0 project. Device testing is required before granting accessibility permissions.
