# Dex2Jar

This is [GoldenBoot's](https://github.com/redragon14368) fork of the [dex2jar](https://github.com/pxb1988/dex2jar) project, aimed at fixing bugs and improving compatibility.

## Key Features and Fixes

- **Updated Libraries**: Upgraded ASM libraries to v9.8.
- **Android Command-Line Support**: Works seamlessly with tools like Termux.
- **Bug Fixes**: Resolved `java.lang.ArrayIndexOutOfBoundsException` errors during class conversion.
- **Improved Codebase**: Reformatted, cleaned up, and optimized the code for better readability and maintainability.
- **Java Compatibility**: Converted some `Java 8` code to `Java 7` to support tools like AIDE.
- **Batch Conversion**: Added support for converting multiple DEX/JAR files in a folder.
- **Packaged Output**: All converted classes are packaged into a single JAR file.
- **Multiple APK**: Supports multiple APK's from a folder

## Getting Started

### Prerequisites

- Ensure you have Java installed (`Java 7` or higher is recommended).
- Download the latest build from the [Releases](https://github.com/redragon14368/Dex2Jar2Dex/releases).

### Installation

1. Download the latest `dexjar.jar` file from the [Releases](https://github.com/redragon14368/Dex2Jar2Dex/releases) page.
2. Place the file in your desired directory.

## Example Usage

#### **Dex to Jar Conversion**
```bash
java -jar dexjar.jar d2j <input> <output_dir>
```
- Where `<input>` is a path of `DEX file` or a `folder` that contains multiple DEX files.
- Where `<output_dir>` is the path of directory where the converted file(s) `.jar` will be saved (optional).

#### **Jar to Dex Conversion** 
```bash
java -jar dexjar.jar j2d <input> <output> <min-sdk>
```

- Where `<input>` is a path of `JAR file` or a `folder` that contains multiple JAR files.
- Where `<output>` is the path of file/directory where the converted file(s) will be saved. It is recommended to use ZIP as the output if you are converting an entire APK.
- Where `<min-sdk>` is the SDK version required for the DEX (optional; default is 13).

## **Contributing**
We welcome contributions! Please follow these steps:

1. Fork the repository.
2. Create a new branch for your feature or bug fix.
3. Commit your changes and push the branch.
4. Submit a pull request with a clear description of your changes.

## License

[Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0.html)

## **Contact**
For any questions or feedback, feel free to open an issue or reach out via [GitHub](https://github.com/redragon14368) or [Telegram](http://telegram.me/GoldenBoot).
