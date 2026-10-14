package com.auctionoverlay;

// The public half of the update signing key (made by Auction + Gamble Overlay\make_updates.py). The mod only
// installs a one-click update whose Ed25519 signature matches it. Not a secret.
final class UpdateKey {
    static final String PUBLIC = "MCowBQYDK2VwAyEApM436TsbHWnuUXNmEEYUOw65/MWgyN1Wr2eVm7dcQ/E=";

    private UpdateKey() {}
}
