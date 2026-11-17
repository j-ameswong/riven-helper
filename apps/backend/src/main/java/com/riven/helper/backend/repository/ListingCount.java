package com.riven.helper.backend.repository;

/**
 * How many of a weapon's live riven auctions share a mastery level, re-roll count
 * and polarity, the attributes a riven search can split a weapon's book on.
 */
public record ListingCount(short masteryLevel, int reRolls, String polarity, long count) {
}
