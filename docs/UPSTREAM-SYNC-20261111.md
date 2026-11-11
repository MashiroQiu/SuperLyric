# Integrated upstream snapshot — 2026-11-11

Upstream: HChenX/SuperLyric master 359238ea0d1fe1142308497de94b97291ddbcc87.
Feature baseline: 601c2d3, including cross-player ownership, API102 reload and Lyricon output.

The integration takes the complete upstream module tree and ports the retained lifecycle/output features into it. The upstream commit is a merge parent, not just an archived side branch. The old provider/service implementation is replaced by the new kernel, source plans, serializers, service runtime, parser fixes, network attribution and upstream regression suites.

Retained behavior includes player ownership, framework-clock progress, native/translated/phonetic lyrics, transient-stop handling, the SystemUI Lyricon bridge, named playback-process initialization, stable callback/service Binders, provider state restoration, and owned worker retirement. The new Lyricon build keeps the previous island/camera geometry, visibility defaults, translation choice and icon corrections; the paired patches concern idle scheduling and immutable shader reuse.

The branch is experimental until the paired APKs are validated on a phone. Android API 102 metadata alone is not evidence of successful live class replacement. JVM/build checks and device checks are recorded separately in API102-HOT-RELOAD.md and POWER-AND-UPGRADE-20261111.md.
