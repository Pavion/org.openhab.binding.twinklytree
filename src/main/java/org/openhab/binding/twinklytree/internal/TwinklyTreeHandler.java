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
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.thing.Channel;
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
            switch (channelUID.getId()) {
                case CHANNEL_SWITCH:
                    if (command instanceof RefreshType) {
                        updateState(channelUID, isOn() ? OnOffType.ON : OnOffType.OFF);
                    } else {
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
                    break;
                case CHANNEL_DIMMER:
                    if (command instanceof RefreshType) {
                        if (isOn()) {
                            updateState(channelUID, new PercentType(getBrightness()));
                        } else {
                            updateState(channelUID, PercentType.ZERO);
                        }
                    } else {
                        PercentType type = (PercentType) command;
                        setBrightness(type.intValue());
                    }
                    break;
                case CHANNEL_MODE:
                    if (command instanceof RefreshType || command.toFullString().toUpperCase().equals("REFRESH")) {
                        updateState(channelUID, new StringType(getMode()));
                    } else {
                        setMode(command.toFullString());
                    }
                    break;
                case CHANNEL_CURRENT_EFFECT:
                    if (command instanceof RefreshType) {
                        updateState(channelUID, new DecimalType(getCurrentEffect()));
                    } else {
                        DecimalType type = (DecimalType) command;
                        setCurrentEffect(type.intValue());
                    }
                    break;
                case CHANNEL_CURRENT_MOVIE:
                    if (command instanceof RefreshType) {
                        updateState(channelUID, new DecimalType(getCurrentMovie()));
                    } else {
                        DecimalType type = (DecimalType) command;
                        setCurrentMovie(type.intValue());
                    }
                    break;
            }
        } catch (IOException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                    "Could not control device at IP address " + config.host);
            logger.error("Error communicating with Twinkly"/* , e */);
            config.token = null;
        }
    }

    private boolean isOn() throws IOException, ProtocolException, MalformedURLException {
        return !"off".equalsIgnoreCase(getMode());
    }

    private String getMode() throws IOException, ProtocolException, MalformedURLException {
        JSONObject getModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/mode"), "GET", null,
                config.token);
        String mode = getModeResponse.getString("mode");
        return mode;
    }

    private void setMode(String newMode) throws IOException, ProtocolException, MalformedURLException {
        sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/mode"), "POST", "{\"mode\":\"" + newMode + "\"}",
                config.token);
    }

    private void setBrightness(int brightness) throws IOException, ProtocolException, MalformedURLException {
        sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/out/brightness"), "POST",
                "{\"mode\":\"enabled\",\"type\":\"A\",\"value\":" + brightness + "}", config.token);
    }

    private int getBrightness() throws IOException, ProtocolException, MalformedURLException {
        JSONObject getModeResponse = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/out/brightness"), "GET",
                null, config.token);
        return getModeResponse.getInt("value");
    }

    private int getCurrentEffect() throws IOException, ProtocolException, MalformedURLException {
        JSONObject response = sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/effects/current"), "GET", null,
                config.token);
        if (response.has("preset_id")) {
            return response.getInt("preset_id");
        } else {
            return response.getInt("effect_id");
        }
    }

    private void setCurrentEffect(int currentEffect) throws IOException, ProtocolException, MalformedURLException {
        sendRequest(new URL(config.getBaseURL(), "/xled/v1/led/effects/current"), "POST",
                "{\"preset_id\":\"" + currentEffect + "\",\"effect_id\":\"" + currentEffect + "\"}", config.token);
    }

    private int getCurrentMovie() throws IOException, ProtocolException, MalformedURLException {
        JSONObject response = sendRequest(new URL(config.getBaseURL(), "/xled/v1/movies/current"), "GET", null,
                config.token);
        return response.getInt("id");
    }

    private void setCurrentMovie(int currentMovie) throws IOException, ProtocolException, MalformedURLException {
        sendRequest(new URL(config.getBaseURL(), "/xled/v1/movies/current"), "POST", "{\"id\":" + currentMovie + "}",
                config.token);
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

        updateStatus(ThingStatus.UNKNOWN);

        Integer refreshRate = 0;
        if (config.refresh != null) {
            refreshRate = config.refresh;
        }
        if (refreshRate > 0) {
            logger.debug("Starting refresh job with {} refresh rate", refreshRate);
            pollingJob = scheduler.scheduleWithFixedDelay(this::refreshState, 0, refreshRate, TimeUnit.SECONDS);
        }

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
        for (Channel channel : this.getThing().getChannels()) {
            if (isLinked(channel.getUID())) {
                handleCommand(channel.getUID(), RefreshType.REFRESH);
            }
        }
    }

    private synchronized void login() {
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

    private synchronized JSONObject sendRequest(URL loginURL, String httpMethod, @Nullable String requestString,
            @Nullable String token) throws ProtocolException {
        JSONObject ret;
        try {
            ret = sendRequestWrapped(loginURL, httpMethod, requestString, token);
        } catch (IOException ex) {
            logger.debug("Invalid Token, attempting to reconnect");
            login();
            try {
                ret = sendRequestWrapped(loginURL, httpMethod, requestString, config.token);
            } catch (IOException ex2) {
                logger.error("Attempt to reconnect failed with an exception: ", ex2);
                ret = new JSONObject();
            }
        }
        return ret;
    }

    private synchronized JSONObject sendRequestWrapped(URL loginURL, String httpMethod, @Nullable String requestString,
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
            // connection.setFixedLengthStreamingMode(out.length);
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
