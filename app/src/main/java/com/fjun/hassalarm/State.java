package com.fjun.hassalarm;

/**
 * Represent a state for a sensor in the hass.io states API
 */
public class State {
    final String state;

    public State(String state) {
        this.state = state;
    }
}
