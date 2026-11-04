package com.capaciti.ops.common;

import java.time.ZoneId;

/** All "per day" / "per week" calculations use the business time zone, not UTC. */
public final class AppTime {
    public static final ZoneId ZONE = ZoneId.of("Africa/Johannesburg");
    private AppTime() {}
}
