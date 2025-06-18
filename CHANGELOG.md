# Changelog

## [Unreleased]

## [5.2.0]
### Changed
- `utils-java-formatting` dependency version updated on [5.3.0](https://github.com/reportportal/utils-java-formatting/releases/tag/5.3.0), by @HardNorth

## [5.1.6]
### Added
- A constructor in `ReportPortalOkHttp3LoggingInterceptor` class which accepts `paramConverter` parameter, by @HardNorth
### Changed
- `utils-java-formatting` dependency version updated on [5.2.7](https://github.com/reportportal/utils-java-formatting/releases/tag/5.2.7), by @HardNorth
### Removed
- `setBodyTypeMap` and `setContentPrettiers` methods from `ReportPortalOkHttp3LoggingInterceptor` class, since they duplicate those from `AbstractHttpFormatter` class, by @HardNorth

## [5.1.5]
### Changed
- Client version updated on [5.2.26](https://github.com/reportportal/client-java/releases/tag/5.2.26), by @HardNorth
- `utils-java-formatting` library version updated on version [5.2.5](https://github.com/reportportal/utils-java-formatting/releases/tag/5.2.5), by @HardNorth

## [5.1.4]
### Changed
- Client version updated on [5.2.13](https://github.com/reportportal/client-java/releases/tag/5.2.13), by @HardNorth
- `utils-java-formatting` library version updated on version [5.2.3](https://github.com/reportportal/utils-java-formatting/releases/tag/5.2.3), by @HardNorth

## [5.1.3]
### Changed
- Client version updated on [5.2.11](https://github.com/reportportal/client-java/releases/tag/5.2.11), by @HardNorth
- `utils-java-formatting` library version updated on version [5.2.2](https://github.com/reportportal/utils-java-formatting/releases/tag/5.2.2), by @HardNorth
- `okhttp`, `client-java` and `jsr305` dependencies marked as `compileOnly`, by @HardNorth

## [5.1.2]
### Changed
- `utils-java-formatting` dependency marked back as `api`, by @HardNorth

## [5.1.1]
### Changed
- Client version updated on [5.2.4](https://github.com/reportportal/client-java/releases/tag/5.2.4), by @HardNorth
- All dependencies are marked as `implementation`, by @HardNorth
### Removed
- `commons-model` dependency to rely on `clinet-java` exclusions in security fixes, by @HardNorth

## [5.1.0]
### Changed
- Client version updated on [5.2.0](https://github.com/reportportal/client-java/releases/tag/5.2.0), by @HardNorth
- `utils-java-formatting` library version updated on version [5.2.0](https://github.com/reportportal/utils-java-formatting/releases/tag/5.2.0), by @HardNorth
- OkHttp3 dependency marked as `implementation` to force users specify their own versions, by @HardNorth

## [5.0.3]
### Changed
- Client version updated on [5.1.22](https://github.com/reportportal/client-java/releases/tag/5.1.22), by @HardNorth
- `utils-java-formatting` library version updated on version [5.1.6](https://github.com/reportportal/utils-java-formatting/releases/tag/5.1.6), by @HardNorth

## [5.0.2]
### Changed
- Client version updated on [5.1.16](https://github.com/reportportal/client-java/releases/tag/5.1.16), by @HardNorth
- `utils-java-formatting` library version updated on version [5.1.5](https://github.com/reportportal/utils-java-formatting/releases/tag/5.1.5), by @HardNorth

## [5.0.1]
### Fixed
- Common field duplication in child class, by @HardNorth
### Changed
- Some refactoring, by @HardNorth
- `utils-java-formatting` library version updated on version [5.1.3](https://github.com/reportportal/utils-java-formatting/releases/tag/5.1.3), by @HardNorth

## [5.0.0]
### Added
- Initial release of OkHttp3 logger, by @HardNorth

## [5.1.0-ALPHA-1]
