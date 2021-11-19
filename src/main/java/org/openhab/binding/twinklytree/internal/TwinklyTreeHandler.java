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

import static org.openhab.binding.twinklytree.internal.TwinklyTreeBindingConstants.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.json.JSONObject;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link TwinklyTreeHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Maarten van Hulsentop - Initial contribution
 * @author Pavion - Refactoring for OH3
 */
@NonNullByDefault
public class TwinklyTreeHandler extends BaseThingHandler {

    private final Logger logger = LoggerFactory.getLogger(TwinklyTreeHandler.class);

    private @Nullable TwinklyTreeConfiguration config;

    private @Nullable ScheduledFuture<?> pollingJob;

    public TwinklyTreeHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        logger.debug("Handle command {} with channel {}", command, channelUID);
        try {
            refreshIfNeeded();
            if (CHANNEL_SWITCH.equals(channelUID.getId())) {
                if (command instanceof RefreshType) {
                    updateState(channelUID, isOn() ? OnOffType.ON : OnOffType.OFF);

                    return;
                }

                if (OnOffType.OFF.equals(command)) {
                    setMode("off");
                    updateState(channelUID, OnOffType.OFF);
                } else if (OnOffType.ON.equals(command)) {
                    setMode("movie");
                    updateState(channelUID, OnOffType.ON);
                } else {
                    logger.warn("Unexpected command for Twinkly: {}", command);
                }
            }
            if (CHANNEL_DIMMER.equals(channelUID.getId())) {
                if (command instanceof RefreshType) {
                    if (isOn()) {
                        updateState(channelUID, new PercentType(getBrightness()));
                    } else {
                        updateState(channelUID, PercentType.ZERO);
                    }
                    return;
                }
                PercentType type = (PercentType) command;
                setBrightness(type.intValue());
            }
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not control device at IP address " + config.host);
            logger.error("Error communicating with Twinkly", e);
            config.token = null;
        }
    }

    private boolean isOn() throws IOException, ProtocolException, MalformedURLException {
        JSONObject getModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/mode"), "GET", null,
                config.token);
        String mode = getModeResponse.getString("mode");
        boolean isOn = !"off".equalsIgnoreCase(mode);
        return isOn;
    }

    private void setBrightness(int brightness) throws IOException, ProtocolException, MalformedURLException {
        JSONObject getModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/out/brightness"), "POST",
                "{\"mode\":\"enabled\",\"type\":\"A\",\"value\":" + brightness + "}", config.token);
    }

    private int getBrightness() throws IOException, ProtocolException, MalformedURLException {
        JSONObject getModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/out/brightness"), "GET",
                null, config.token);
        return getModeResponse.getInt("value");
    }

    private void setMode(String newMode) throws IOException, ProtocolException, MalformedURLException {
        JSONObject setModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/mode"), "POST",
                "{\"mode\":\"" + newMode + "\"}", config.token);
    }

    private void logout() {
        updateStatus(ThingStatus.OFFLINE);
        try {
            sendRequest(new URL(config.getBaseURL(), "/xled/v1/logout"), "POST", "{}", config.token);
        } catch (IOException e) {
            logger.debug("Error while logout", e);
        }
    }

    private synchronized void refreshIfNeeded() {
        if (config.token == null || isTokenExpired()) {
            if (config.token != null) {
                logout();
            }
            login();
        }
    }

    @Override
    public void initialize() {
        logger.debug("Start initializing!");
        config = getConfigAs(TwinklyTreeConfiguration.class);

        // TODO: Initialize the handler.
        // The framework requires you to return from this method quickly. Also, before leaving this method a thing
        // status from one of ONLINE, OFFLINE or UNKNOWN must be set. This might already be the real thing status in
        // case you can decide it directly.
        // In case you can not decide the thing status directly (e.g. for long running connection handshake using WAN
        // access or similar) you should set status UNKNOWN here and then decide the real status asynchronously in the
        // background.

        // set the thing status to UNKNOWN temporarily and let the background task decide for the real status.
        // the framework is then able to reuse the resources from the thing handler initialization.
        // we set this upfront to reliably check status updates in unit tests.
        updateStatus(ThingStatus.UNKNOWN);

        // Example for background initialization:
        // scheduler.execute(() -> {
        // login();
        pollingJob = scheduler.scheduleWithFixedDelay(this::refreshState, 100, 1, TimeUnit.MINUTES);
        // if (token != null) {
        // updateStatus(ThingStatus.ONLINE);
        // } else {
        // updateStatus(ThingStatus.OFFLINE);
        // }
        // });

        logger.debug("Finished initializing!");

        // Note: When initialization can NOT be done set the status with more details for further
        // analysis. See also class ThingStatusDetail for all available status details.
        // Add a description to give user information to understand why thing does not work as expected. E.g.
        // updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
        // "Can not access device as username and/or password are invalid");
    }

    private void refreshState() {
        try {
            refreshIfNeeded();

            boolean isOn = isOn();
            updateState(CHANNEL_SWITCH, isOn ? OnOffType.ON : OnOffType.OFF);

            int brightnessPct = 0;
            if (isOn) {
                brightnessPct = getBrightness();
            }
            updateState(CHANNEL_DIMMER, new PercentType(brightnessPct));
        } catch (IOException e) {
            config.token = null;
            logger.error("Issue while polling for state ", e);
        }
    }

    private void login() {
        try {
            config.token = null;

            JSONObject loginResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/login"), "POST",
                    "{\"challenge\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}", null);
            String unverifiedToken = loginResponse.getString("authentication_token");
            String challengeResponse = loginResponse.getString("challenge-response");
            Long tokenExpiresIn = loginResponse.getLong("authentication_token_expires_in");

            logger.debug("Twinkly sent login token {} with challenge {}", unverifiedToken, challengeResponse);

            JSONObject verifyResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/verify"), "POST",
                    "{\"challenge-response\":\"" + challengeResponse + "\"}", unverifiedToken);
            config.token = unverifiedToken;
            config.tokenExpiryDate = new Date(System.currentTimeMillis() + (tokenExpiresIn.longValue() * 1000));
            updateStatus(ThingStatus.ONLINE);
        } catch (IOException e) {
            logger.error("Error while connecting to twinkly ", e);
        }
    }

    @Override
    public void dispose() {
        pollingJob.cancel(true);
    }

    private boolean isTokenExpired() {
        return config.tokenExpiryDate.before(new Date());
    }

    private JSONObject sendRequest(URL loginURL, String httpMethod, @Nullable String requestString,
            @Nullable String token) throws IOException, ProtocolException {
        byte[] out = null;
        HttpURLConnection connection = (HttpURLConnection) loginURL.openConnection();
        if (token != null) {
            connection.setRequestProperty("X-Auth-Token", token);
        }
        connection.setRequestMethod(httpMethod);
        if (requestString != null) {
            out = requestString.getBytes(StandardCharsets.UTF_8);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            connection.setFixedLengthStreamingMode(out.length);
            connection.setDoOutput(true);
        }
        connection.setDoInput(true);
        connection.connect();

        if (out != null) {
            try (OutputStream os = connection.getOutputStream()) {
                os.write(out);
            }
        }

        StringBuilder textBuilder = new StringBuilder();
        try (Reader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), Charset.forName(StandardCharsets.UTF_8.name())))) {
            int c = 0;
            while ((c = reader.read()) != -1) {
                textBuilder.append((char) c);
            }
        }

        if (logger.isDebugEnabled()) {
            logger.debug("Request {} {} {} got response headers {} with data {} ", httpMethod, loginURL, requestString,
                    connection.getHeaderFields(), textBuilder);
        }
        JSONObject loginResponse = new JSONObject(textBuilder.toString());
        return loginResponse;
    }
}
