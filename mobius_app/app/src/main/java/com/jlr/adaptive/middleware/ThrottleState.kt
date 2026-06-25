package com.jlr.adaptive.middleware

enum class ThrottleState {
    NORMAL,         // CPU within acceptable range
    OVERTHROTTLED,  // CPU sustained above high threshold — quality being reduced
    RECOVERING,     // CPU sustained below low threshold — quality being restored
    COOLDOWN,       // Recent quality change — waiting before next decision
}
