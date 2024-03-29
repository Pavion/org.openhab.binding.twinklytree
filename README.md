# TwinklyTree Binding

This binding adds native support for the Twinkly christmas LED lights to openHAB.

## Supported Things

The binding supports the xled API as is described in https://xled-docs.readthedocs.io/en/latest/rest_api.html . It has been tested with the 250 led Twinkly gen II

## Discovery

Auto discovery is not implemented yet, so you need to provide the IP address or hostname of the Twinkly controller when adding the Thing

## Binding Configuration

To start with the binding, put the jar in the add-ons directory and start adding the Thing

## Thing Configuration

| parameter     | type   | description                                       |
|---------------|--------|---------------------------------------------------|
| host          | String | IP or hostname of your device                     |
| refresh       | Number | Refresh rate in seconds (default 0 to disable)    |

## Channels

| channel       | type   | description                               |
|---------------|--------|-------------------------------------------|
| switch        | Switch | Turn the lights on (in movie mode) or off |
| dimmer        | Dimmer | Adjust brightness                         |
| mode          | String | Set current mode                          |
| currenteffect | Number | Set current effect for effect mode        |
| currentmovie  | Number | Set current movie for movie mode          |
| color         | Color  | Set color for color mode                  |

## Textual configuration example

### .things

```
Thing twinklytree:twinkly:twinklyTree "Twinkly Tree" @ "MyRoom" [ host="192.168.0.2", refresh="60" ]
```

### .items (with Alexa support)

```
Switch TwinklyTreeSwitch "Twinkly Tree Switch" { channel="twinklytree:twinkly:twinklyTree:switch", alexa="PowerController.powerState" [friendlyNames="Twinkly My Room"] }
```
