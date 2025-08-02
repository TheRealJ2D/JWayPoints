[![JWaypoints Plugin](https://i.postimg.cc/6Q2Rbyjg/jwp.png)](https://modrinth.com/plugin/jwaypoints)


# JWaypoints

![Visitor Count](https://visitor-badge.laobi.icu/badge?page_id=TheRealJ2D.JWaypoints)
![License](https://img.shields.io/github/license/TheRealJ2D/JWaypoints)
![GitHub release (latest by date)](https://img.shields.io/github/v/release/TheRealJ2D/JWaypoints)

A feature-rich waypoints plugin for Minecraft servers with arrow navigation, customizable designs, and distance display.

## Features

- **Arrow Navigation**: Visual 3D arrow guides that point to your waypoint destination
- **Customizable Designs**: Multiple arrow designs with different materials, glowing effects, and particle trails
- **GUI Management**: User-friendly interface for managing waypoints
- **Distance Display**: Shows remaining distance using the XP bar
- **Arrival Effects**: Customizable sounds and particle effects when reaching destinations
- **Waypoint Sharing**: Share waypoints with other players
- **Smart Guidance**: Switches between arrow and compass navigation based on visibility
- **Multiple Patterns**: Standard, spiral, zigzag, and static arrow patterns

## Installation

1. Download the latest release from the [Releases](https://github.com/TheRealJ2D/JWaypoints/releases) page
2. Place the `JWaypoints-X.X.jar` file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin using `/plugins/JWaypoints/config.yml` if needed

## Requirements

- **Minecraft Version**: 1.21+
- **Server Software**: Spigot, Paper, or compatible forks
- **Java Version**: 17+

## Commands

| Command | Description | Usage |
|---------|-------------|-------|
| `/waypoint set <name> [x y z]` | Create a new waypoint | `/wp set home` |
| `/waypoint remove <name>` | Delete a waypoint | `/wp remove home` |
| `/waypoint list` / `/waypoint gui` | Open waypoint management GUI | `/wp gui` |
| `/waypoint activate <name>` | Start following a waypoint | `/wp activate home` |
| `/waypoint deactivate` | Stop following current waypoint | `/wp deactivate` |
| `/waypoint share <name> <player>` | Share waypoint with another player | `/wp share home Steve` |
| `/waypoint current` | Show active waypoint information | `/wp current` |
| `/waypoint design <name>` | Change arrow design | `/wp design diamond` |
| `/waypoint designs` | List available designs | `/wp designs` |
| `/waypoint sound <name>` | Change arrival sound | `/wp sound bell` |
| `/waypoint sounds` | List available sounds | `/wp sounds` |
| `/waypoint particle <name>` | Change arrival particle effect | `/wp particle firework` |
| `/waypoint particles` | List available particles | `/wp particles` |
| `/waypoint distance` | Toggle distance display | `/wp distance` |
| `/waypoint guidance` | Toggle guidance messages | `/wp guidance` |
| `/waypoint silent` | Toggle silent mode | `/wp silent` |
| `/waypoint nosound` | Toggle arrival sounds | `/wp nosound` |
| `/waypoint noparticles` | Toggle arrival particles | `/wp noparticles` |
| `/waypoint updatecheck` | Check for plugin updates | `/wp updatecheck` |

### Aliases
- `/wp` - Short alias for `/waypoint`

## Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `jwaypoints.use` | Use waypoint commands | `true` |
| `jwaypoints.set` | Create waypoints | `true` |
| `jwaypoints.remove` | Delete waypoints | `true` |
| `jwaypoints.activate` | Follow waypoints | `true` |
| `jwaypoints.deactivate` | Stop following waypoints | `true` |
| `jwaypoints.share` | Share waypoints | `true` |
| `jwaypoints.receive` | Receive shared waypoints | `true` |
| `jwaypoints.gui` | Use waypoint GUI | `true` |
| `jwaypoints.design` | Change arrow designs | `true` |
| `jwaypoints.sound` | Change arrival sounds | `true` |
| `jwaypoints.distance` | Toggle distance display | `true` |
| `jwaypoints.edit` | Edit existing waypoints | `true` |
| `jwaypoints.settings` | Change plugin settings | `true` |
| `jwaypoints.particles` | Change arrival particles | `true` |
| `jwaypoints.updatecheck` | Check for updates | `op` |
| `jwaypoints.admin` | Administrative permissions | `op` |
| `jwaypoints.bypass.limit` | Bypass waypoint limits | `op` |

## Configuration

The plugin creates a `config.yml` file with the following main sections:

### Arrow Settings
```yaml
arrow:
  height: 3.0                    # Height of arrow above player
  spacing: 0.4                   # Space between arrow parts
  forward_offset: 2.0            # Distance in front of player
  default_material: "LIME_CONCRETE"
  head_material: "YELLOW_CONCRETE"
  tail_material: "RED_CONCRETE"
```

### Plugin Settings
```yaml
settings:
  allow_sharing: true            # Enable waypoint sharing
  waypoint_limit: 10            # Max waypoints per player
  distance_display_default: true # Default distance display setting
  max_waypoints_per_page: 45    # GUI pagination limit
  absolute_max_waypoints: 100   # Hard limit for all players
```

### Arrow Designs
The plugin includes several pre-configured designs:
- **Standard**: Basic concrete arrow
- **Diamond**: Glowing diamond blocks with particles
- **Gold**: Golden blocks with flame particles
- **Emerald**: Emerald blocks with happy villager particles
- **Redstone**: Redstone blocks with redstone particles

### Sounds & Particles
Customize arrival effects with various sounds and particle effects available in the config.

## Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/TheRealJ2D/JWaypoints.git
   cd JWaypoints
   ```

2. Build with Maven:
   ```bash
   mvn clean package
   ```

3. The compiled JAR will be in the `target/` directory

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Support

- **Issues**: Report bugs or request features on [GitHub Issues](https://github.com/TheRealJ2D/JWaypoints/issues)
- **Wiki**: Check the [Wiki](https://j2dplugins.xyz/jwp.html) for detailed documentation

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## bStats

This plugin uses bStats to collect anonymous usage statistics. You can opt-out by setting `enabled: false` in `plugins/bStats/config.yml`.

[![bStats](https://bstats.org/signatures/bukkit/JWaypoints.svg)](https://bstats.org/plugin/bukkit/JWaypoints/26413)

## Version History

- **v1.5**: Current version with enhanced GUI, particle effects, and improved navigation
- See [Releases](https://github.com/TheRealJ2D/JWaypoints/releases) for full changelog

---

Made with ❤️ by [J2D](https://github.com/TheRealJ2D)
