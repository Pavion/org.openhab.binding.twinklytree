/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;

import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link TwinklyTreeConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author Maarten van Hulsentop - Initial contribution
 */
public class TwinklyTreeConfiguration {

    /**
     * Sample configuration parameter. Replace with your own.
     */
    public String host;
    protected @Nullable String token;
    protected Date tokenExpiryDate = new Date();

    public URL getBaseURL() throws MalformedURLException {
        return new URL("http://" + host);
    }
}
