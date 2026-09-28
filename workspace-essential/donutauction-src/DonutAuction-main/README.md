DonutAuctionHouse is a standalone auction house plugin for Paper/Folia servers (Minecraft 1.20+). It offers a clean, intuitive GUI-powered marketplace where players can list, browse, search, and purchase items using any Vault-compatible economy. With optional DonutCore integration, the plugin can resolve player display names and enhance the browsing experience in networks.
Key Features

    GUI-based Auction Browser
        54‑slot inventory with pagination
        Sorting, filtering by category, and text search
        Flexible controls: price sort, filter, refresh, search, and personal listings

    Player Auction Management
        View, cancel, and collect on your own listings
        Sold, expired and active auctions are separated with clear status lines
        Cancellation returns items instantly and prevents duping

    Vault Economy Support
        Works with any economy plugin supported by Vault (EssentialsX, iConomy, etc.)
        Money is transferred instantly at listing or purchase time
        Customizable max price and listing duration via configuration

    Configurable Storage
        Default SQLite support for lightweight setups
        Optional MySQL backing for larger networks
        HikariCP connection pooling for high performance

    DonutCore Hook (Optional)
        When DonutCore is present, player names in listings are resolved with the network’s display name system
        Graceful no‑op support if DonutCore is absent—no errors or dependencies

    Asynchronous & Safe
        All database operations run off the main thread
        Executor abstraction ensures server responsiveness

    Modern Codebase
        Written in Java 17 with Maven build system
        Clean modular architecture (service, storage, GUI, hooks)
        Shaded dependencies prevent conflicts with other plugins
        Easy to extend or fork for custom behavior

    Customizable Messages & Appearance
        Titles and button names can be adjusted via source or messages support
        Colour scheme now uses neutral (white) text with gold headers for clarity
