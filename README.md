# Pathmind

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.x-00AA00?style=for-the-badge&logo=minecraft)](https://minecraft.net)
[![Fabric](https://img.shields.io/badge/Fabric-0.17.2-CC6E3E?style=for-the-badge&logo=modrinth)](https://fabricmc.net)
[![Java](https://img.shields.io/badge/Java-21+-FF6B6B?style=for-the-badge&logo=openjdk)](https://openjdk.java.net)
[![License](https://img.shields.io/badge/License-CC%20BY--NC%204.0-blue?style=for-the-badge)](https://creativecommons.org/licenses/by-nc/4.0/)
[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)](https://github.com/soymods/pathmind)

A Minecraft Fabric mod that introduces a visual node editor system for creating complex workflows and automation sequences through an intuitive graphical interface.

## Quick Start

### Prerequisites
- **Minecraft**: any 1.21.x release
- **Fabric Loader**: 0.17.2 or higher
- **Fabric API**: latest release for your 1.21.x version
- **Baritone API**: 1.21.x release coorisponding with your version
- **Java**: 21 or higher

### Installation

1. **Install Fabric Loader**
   - Download and install Fabric Loader for Minecraft 1.21.x
   - [Download from FabricMC](https://fabricmc.net/use/installer/)

2. **Install Fabric API**
   - Download the latest Fabric API for your 1.21.x version
   - [Download from Modrinth](https://modrinth.com/mod/fabric-api)

3. **Install Pathmind**
   - Download the latest Pathmind mod jar
   - Place it in your `mods` folder

4. **Install Fabric API**
   - Download the the Baritone API build coorisponding to your version
   - [Download from GitHub](https://github.com/cabaletta/baritone/releases)

5. **Launch and Enjoy!**
   - Start Minecraft with Fabric Loader
   - Use your configured keybind to open the visual editor

## Development

### Building from Source

1. **Clone the Repository**
   ```bash
   git clone https://github.com/soymods/pathmind.git
   cd pathmind
   ```

2. **Generate Sources**
   ```bash
   ./gradlew genSources
   ```

3. **Import to IDE**
   - Import as a Gradle project
   - Wait for dependencies to resolve

4. **Build the Mod**
   ```bash
   ./gradlew build
   ```
   Output will be in `build/libs/`

5. **Run in Development**
   ```bash
   ./gradlew runClient
   ```

### Project Structure

```
src/main/java/com/pathmind/
├── nodes/           # Node system implementation
│   ├── Node.java    # Core node class
│   ├── NodeType.java # Node type definitions
│   └── NodeConnection.java # Connection handling
├── ui/              # User interface components
│   └── NodeGraph.java # Main node graph rendering
├── screen/          # Screen implementations
│   └── PathmindVisualEditorScreen.java # Main editor screen
├── PathmindMod.java # Main mod class
└── PathmindKeybinds.java # Keybind configuration
```

## Version Information

| Component | Version |
|-----------|---------|
| **Mod Version** | 1.0.0 |
| **Minecraft Version** | 1.21.x (built & tested on 1.21.8) |
| **Yarn Mappings** | 1.21.8+build.1 (with :v2 for compilation) |
| **Fabric Loader** | 0.17.2 |
| **Fabric API** | Latest 1.21.x-compatible release |
| **Baritone API** | 1.15.0 (bundled) |

### Development Guidelines
- Follow Java coding conventions
- Add comments for complex logic
- Test your changes thoroughly
- Update documentation as needed

## License

This project is distributed under the custom **Pathmind License (All Rights Reserved)** as described in the [`LICENSE`](LICENSE)
file. In summary:

- Redistribution, modification, or re-uploading of the mod is **not permitted** without explicit written permission from the
  author.
- You **may create and monetize videos** featuring the mod.
- Inclusion in modpacks is allowed only if monetization is limited to CurseForge or Modrinth (including sponsored links or
  banners) unless prior written permission is granted. Modpacks distributed elsewhere must clearly credit the Pathmind project,
  must not be easily confused with Pathmind or the author's other projects, and must end any additional monetization upon
  request.
- The mod is provided “as is” without warranty—use at your own risk.

## Bug Reports & Feature Requests

Found a bug or have an idea? We'd love to hear from you!

- **Bug Reports**: [Open an Issue](https://github.com/soymods/pathmind/issues/new?template=bug_report.md)
- **Feature Requests**: [Open an Issue](https://github.com/soymods/pathmind/issues/new?template=feature_request.md)
- **General Discussion**: [Join our Discord](https://discord.gg/zWT2zxQm)

## Acknowledgments

- **FabricMC Team** for the modding framework
- **Minecraft Community** for inspiration and feedback
- **Blender Foundation** & **Scratch Foundation** for inspiring the node-based interface design

## Support

Need help? Here are some resources:

- **Documentation**: Check this README and in-game tooltips
- **Issues**: [GitHub Issues](https://github.com/soymods/pathmind/issues)
- **Discord**: [Join our Community](https://discord.gg/zWT2zxQm)

---

<div align="center">

[![GitHub](https://img.shields.io/badge/GitHub-Repository-black?style=for-the-badge&logo=github)](https://github.com/soymods/pathmind)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-00D5AA?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/pathmind)

</div>
