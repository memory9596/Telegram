#pragma once

#include <algorithm>
#include <cstdint>
#include <map>
#include <utility>

// Rebuilt from running requests on each scheduling pass, so cancellation,
// retries and DC migration cannot leak reservations.
class DownloadRequestBudget {
public:
    explicit DownloadRequestBudget(bool premium) : limit((premium ? 16u : 1u) * 1024 * 1024) {
    }

    void account(uint32_t dc, bool small, uint32_t bytes) {
        used[{dc, small}] += bytes;
    }

    bool available(uint32_t dc, bool small, uint32_t bytes) const {
        if (bytes == 0) {
            return true; // Control requests must not wait behind file data.
        }
        auto iter = used.find({dc, small});
        uint64_t current = iter == used.end() ? 0 : iter->second;
        return bytes <= limit && current <= limit - bytes;
    }

private:
    const uint32_t limit;
    std::map<std::pair<uint32_t, bool>, uint64_t> used;
};

// Account-wide byte pacing for one file-transfer direction. Server waits
// remain per-DC, while the learned rate prevents another cross-file burst as
// soon as the wait expires.
class FileRequestScheduler {
public:
    bool acquire(uint32_t dc, uint32_t bytes, int64_t now, bool premium) {
        if (bytes == 0) return true;
        updateProfile(premium, now);
        auto &state = states[dc];
        if (now < state.blockedUntil) {
            scheduleWakeup(state.wakeupAt, state.blockedUntil, now);
            return false;
        }
        if (state.wakeupAt <= now) state.wakeupAt = 0;
        if (tokenWakeupAt > now) return false;
        tokenWakeupAt = 0;

        uint64_t capacity = std::max<uint64_t>(burstBytes(premium), bytes);
        refill(capacity, now);
        if (availableBytes < bytes) {
            uint64_t missingBytes = bytes - availableBytes;
            uint64_t delay = (missingBytes * 1000 + rateBytesPerSecond - 1) / rateBytesPerSecond;
            scheduleWakeup(tokenWakeupAt, now + (int64_t) std::max<uint64_t>(1, delay), now);
            return false;
        }
        availableBytes -= bytes;
        if (state.resumeSlowly) {
            availableBytes = 0;
            lastRefillAt = now;
            state.resumeSlowly = false;
        }
        return true;
    }

    void onFloodWait(uint32_t dc, int64_t now, uint32_t waitSeconds, bool premium, bool premiumFloodWait) {
        updateProfile(premium, now);
        auto &state = states[dc];
        bool newRound = now >= state.blockedUntil;
        int64_t waitMillis = (int64_t) std::max(1u, waitSeconds) * 1000;
        state.blockedUntil = std::max(state.blockedUntil, now + waitMillis);
        state.resumeSlowly = true;
        scheduleWakeup(state.wakeupAt, state.blockedUntil, now);

        bool reduceRate = newRound && now >= rateBackoffUntil;
        rateBackoffUntil = std::max(rateBackoffUntil, state.blockedUntil);
        nextRateIncreaseAt = now + 60000;
        availableBytes = 0;
        lastRefillAt = now;
        if (reduceRate) {
            rateBytesPerSecond = std::max(minimumRate(premium), rateBytesPerSecond / 2);
        }
        if (premiumFloodWait && !premium) premiumFloodLimited = true;
    }

    void onRequestSucceeded(int64_t now, bool premium) {
        updateProfile(premium, now);
        uint64_t targetRate = initialRate(premium);
        if (premiumFloodLimited || rateBytesPerSecond >= targetRate
                || now < rateBackoffUntil || now < nextRateIncreaseAt) return;
        rateBytesPerSecond = std::min(targetRate, rateBytesPerSecond + std::max<uint64_t>(1, rateBytesPerSecond / 20));
        nextRateIncreaseAt = now + 60000;
        tokenWakeupAt = 0;
    }

    int nextDelay(int64_t now) {
        int64_t next = 0;
        bool due = false;
        if (tokenWakeupAt != 0) {
            if (tokenWakeupAt <= now) {
                tokenWakeupAt = 0;
                due = true;
            } else {
                next = tokenWakeupAt;
            }
        }
        for (auto &entry : states) {
            auto &state = entry.second;
            if (state.wakeupAt == 0) continue;
            if (state.wakeupAt <= now) {
                state.wakeupAt = 0;
                due = true;
            } else if (next == 0 || state.wakeupAt < next) {
                next = state.wakeupAt;
            }
        }
        if (due) return 0;
        return next == 0 ? -1 : (int) std::min<int64_t>(1000, next - now);
    }

    void clear() {
        *this = FileRequestScheduler();
    }

    void clear(uint32_t dc) {
        states.erase(dc);
    }

private:
    struct State {
        int64_t blockedUntil = 0;
        int64_t wakeupAt = 0;
        bool resumeSlowly = false;
    };

    static uint64_t initialRate(bool premium) {
        return (premium ? 16u : 1u) * 1024 * 1024;
    }

    static uint64_t minimumRate(bool premium) {
        return (premium ? 1024u : 64u) * 1024;
    }

    static uint64_t burstBytes(bool premium) {
        return (premium ? 2048u : 256u) * 1024;
    }

    static void scheduleWakeup(int64_t &wakeupAt, int64_t at, int64_t now) {
        if (wakeupAt <= now || at < wakeupAt) wakeupAt = at;
    }

    void updateProfile(bool premium, int64_t now) {
        if (rateBytesPerSecond == 0) {
            premiumUser = premium;
            rateBytesPerSecond = initialRate(premium);
            return;
        }
        if (premiumUser == premium) return;
        premiumUser = premium;
        premiumFloodLimited = false;
        rateBytesPerSecond = initialRate(premium);
        availableBytes = 0;
        lastRefillAt = now;
        nextRateIncreaseAt = 0;
        rateBackoffUntil = 0;
        tokenWakeupAt = 0;
    }

    void refill(uint64_t capacity, int64_t now) {
        if (lastRefillAt < 0) {
            availableBytes = capacity;
            lastRefillAt = now;
            return;
        }
        availableBytes = std::min(availableBytes, capacity);
        if (now <= lastRefillAt) return;
        uint64_t elapsed = now - lastRefillAt;
        uint64_t fillMillis = (capacity * 1000 + rateBytesPerSecond - 1) / rateBytesPerSecond;
        uint64_t added = elapsed >= fillMillis ? capacity : elapsed * rateBytesPerSecond / 1000;
        availableBytes = std::min(capacity, availableBytes + added);
        lastRefillAt = now;
    }

    std::map<uint32_t, State> states;
    bool premiumUser = false;
    bool premiumFloodLimited = false;
    uint64_t availableBytes = 0;
    uint64_t rateBytesPerSecond = 0;
    int64_t lastRefillAt = -1;
    int64_t nextRateIncreaseAt = 0;
    int64_t rateBackoffUntil = 0;
    int64_t tokenWakeupAt = 0;
};
