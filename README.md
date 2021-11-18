# TwinklyTree Binding

_This binding adds native support for the Twinkly christmas LED lights to openHAB. Currently, only on/off is supported as this is the only function that i need right now. But now there is a framework, other features can be implemented as well. I can imagine supporting dimmer functionality to be implemented soon_

## Supported Things

_The binding supports the xled API as is described in https://xled-docs.readthedocs.io/en/latest/rest_api.html . It has been tested with the 250 led Twinkly gen II_

## Discovery

_Auto discovery is not implemented yet, so you need to provide the IP address or hostname of the Twinkly controller when adding the Thing_

## Binding Configuration

_To start with the binding, put the jar in the add-ons directory and start adding the Thing_

## Thing Configuration

_You need to manually specify the IP address of the Twinkly controller_

## Channels

_There currently only is the on/off switch channel_


| channel  | type   | description                           |
|----------|--------|---------------------------------------|
| switch   | Switch | The channel to turn the lights on/off |

