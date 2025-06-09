# ChatterMod
 A Fabric 1.21.5 client-side mod whose job is to periodically pull YouTube and Twitch Live Chat messages and inject them into Minecraft’s in-game chat. This can work with either multistreaming, or streaming on a single platform. 

NOTE: PLEASE READ BELOW TO LEARN HOW TO PUT IN API KEYS!
 

**Features:**

- Platform Indicators: Each message is prefixed with a toggleable tag ([YT] or [TW]) so you always know where it came from.

- Customizable Colors: Make each platform's name tag distinct with toggleable colors (defaults to YouTube Red and Twitch Purple).

- In-Game Configuration: No need to restart! Use simple in-game commands to set up your accounts, toggle features, and reload the mod on the fly.

-  Client-Side & Server-Safe: ChatterMod runs entirely on your client. It does not require any server-side installation and is safe to use on any public or private server.

 **How to Install**

-     Make sure you have Fabric Loader and Fabric API for Minecraft 1.21.5 installed.

-     Download the ChatterMod-1.0.0-BETA-all.jar file.

-     Drop the downloaded JAR file into your .minecraft/mods/ folder.

-     Launch the game!


**How to Configure:**

You can configure everything using in-game commands. The mod will generate a config/chattermod.properties file, but you should not need to edit it manually.

**In-Game Commands:**

    /chattermod toggle logos - Turns the [YT] and [TW] tags on or off.
    /chattermod toggle colors - Turns the custom author name colors on or off.
    /chattermod reload - Reloads all settings and reconnects to the chat servers. Use this after changing your credentials.

YouTube Setup:

    /chattermod youtube set apikey <your-youtube-api-key>
    /chattermod youtube set channel <your-youtube-channel-id>
     To get your YouTube API Key: Visit https://developers.google.com/youtube/v3/getting-started, log in, and copy the entire token.
Twitch Setup:

    /chattermod twitch set channel <your-twitch-username>
    /chattermod twitch set token <your-oauth-token>
        To get your Twitch token: Visit https://twitchapps.com/tmi/, log in, and copy the entire token (it will start with oauth:).

After setting your credentials, run /chattermod reload to connect!

**Manual Configuration (Optional)**

For reference, here is what the config/chattermod.properties file looks like. It will be generated automatically in your .minecraft/config/ folder.

#ChatterMod BETA Configuration
#Generated on...
general.showPlatformLogo=true
general.usePlatformColors=true
colors.youtube=RED
colors.twitch=DARK_PURPLE
youtube.1.channelId=UCYOURCHANNELID_HERE
youtube.1.apiKey=YOUR_API_KEY_HERE
twitch.1.channelName=your_twitch_channel_name
twitch.1.oauthToken=YOUR_OAUTH_TOKEN_HERE

**Beta Notes**

    _This is a beta release, so bugs may be present. Please report any issues you find!
    The next major planned feature is a full in-game configuration screen via Mod Menu, which will replace the need for most commands._
