/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.twinklytree.internal;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.smarthome.core.thing.ThingTypeUID;

/**
 * The {@link TwinklyTreeBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author Maarten van Hulsentop - Initial contribution
 */
@NonNullByDefault
public class TwinklyTreeBindingConstants {

    private static final String BINDING_ID = "twinklytree";

    // List of all Thing Type UIDs
    public static final ThingTypeUID THING_TYPE_TWINKLY = new ThingTypeUID(BINDING_ID, "twinkly");

    // List of all Channel ids
    public static final String CHANNEL_SWITCH = "switch";
    public static final String CHANNEL_DIMMER = "dimmer";
}
