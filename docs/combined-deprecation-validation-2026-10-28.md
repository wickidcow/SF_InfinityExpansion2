# IE2 combined build and deprecation validation

This draft combines the exact source changes from PRs #40, #41, #42 and #43; it is not a release authorization. No versions, gameplay rates, energy/storage APIs, persisted IDs or release-publishing guards are changed.

Input heads:
- #40 Gradle wrapper/checksum and matching guard: a51d6ef513c4ad0a967febb546beab82033342e7
- #41 Doctor Adventure messages and color tests: e274330489e878e86bf9914acdcb5afe86b78d54
- #42 display-only PluginMeta accessor: 7136411edf0bcd5a3bc8f19ad300d8ea77a7e0f1
- #43 research level-cost accessor/tests and testCompileOnly correction: 16ef8a2c17a7ffd5e6b13f817c21caf4b1b1efd1

The existing CI still validates its original pinned core and Networks inputs. A separate read-only workflow additionally builds against Slimefun Legacy candidate f5656f43d220de1fac59e1492eb1f5248af129ab and checks transport contracts against Networks candidate 7c6a36b26ddb294ee5775454449d996c02cb9dc6.

Require both workflows to pass for this exact combined source, including actually executed Doctor/research JUnit tests, no shaded core/test dependencies and Java 21 release bytecode. The 26.2/26.3 Java 25 source-compilation outputs are not release artifacts. Neither compile checks nor unit tests certify live-world recovery, Folia ownership or every addon combination. No production deployment or release publication is performed.
