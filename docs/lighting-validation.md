# Lighting guidance validation

Status: **provisional; not empirically calibrated**. Profile: `CaptureSpec.LIGHTING_PROFILE_VERSION`
(`provisional-v2`). Both the measurer and policy use `CaptureSpec.lighting` by default.
Change the profile version when changing thresholds or measurement semantics. Synthetic tests
establish behavior, not accuracy across cameras or people.

## What the indicator means

- **Checking lighting**: insufficient or invalid regional samples, unacceptable pose, missing
  measurements, or a gap greater than 300 ms. New assessments must settle again.
- **Lighting looks okay**: no supported warning persisted. This is advisory, not a certification
  of uniform lighting, exposure, skin appearance, or capture quality.
- **Face a soft, even light source**: simultaneous dark and bright evidence, a top/bottom
  imbalance, or imbalance on both axes. No side is inferred for these cases.
- Left/right instructions: a horizontal imbalance without a vertical imbalance.
- Overall darkness or brightness takes priority over directional imbalance. Mixed exposure
  takes priority over either exposure instruction. Positioning corrections remain primary.

The inner ellipse and trimmed regional means suppress some outliers but do not segment skin
or prove spatial uniformity. Small patches or symmetric patterns can escape half-face means.
Camera processing, skin reflectance, facial hair and accessories are potential confounders to
evaluate. The positive wording deliberately reflects these limits.

## Current profile

| Check | Enter | Exit while active |
| --- | --- | --- |
| Dark median (Y byte) | <55 | >=65, provided shadow fraction also clears |
| Bright median (Y byte) | >205 | <=195, provided highlight fraction also clears |
| Shadow fraction (Y <=24) | >=25% | <20% |
| Highlight fraction (Y >=235) | >=20% | <15% |
| Regional difference AND darker/brighter ratio, either axis | >=28 AND <=0.75 | difference <=20 OR ratio >=0.82 |

Warnings require 400 ms persistence; acceptable readings require 700 ms. Mixed exposure retains
both exposure exit bands until one or both problems clear. At least 96 total samples and 32 in
each of the left, right, top and bottom regions are mandatory. Both axes use the existing
imbalance thresholds as provisional starting points, not newly validated vertical cutoffs.

## Device acceptance and calibration protocol

Run controlled front-camera sessions across multiple device models and consenting participants
covering varied skin tones, facial hair and glasses. Include diffuse frontal light, dim light,
strong direct light, backlight, left/right light, overhead/under-light, mixed bright/shadowed
regions, and localized or symmetric patches. Repeat at supported face sizes and pose limits.

For each trial, record profile version, device/OS, scenario, expected useful instruction,
observed instruction, time to warning/recovery, and any incorrect side or prompt oscillation.
If collecting diagnostics, retain only consented aggregate metrics and timestamps; never raw
camera frames in app logs or persistent storage. Track missed warnings and false warnings
separately by scenario, device and participant group. Assess whether following the instruction
actually improves the result, including mixed-light scenes.

Verify mirror/crop/rotation alignment, insufficient regional coverage, interruptions longer
than 300 ms, face loss/recovery, session restart and stable compact-indicator behavior. Verify
that camera auto-exposure settling does not cause repeated conflicting instructions.

Tune on a development set and assess on held-out participants/devices before promoting a
profile. Record the sample counts, error rates, latency distributions, chosen acceptance
criteria and remaining failure cases alongside the profile version. Do not claim calibration
from synthetic unit tests or a single device screenshot.

## Evidence

Automated fixtures cover horizontal and vertical imbalance, mixed exposure, actual entry/exit
boundaries, fraction triggers, warning persistence, interrupted side changes, timestamp
rejection and unavailable/invalid measurements. UI tests cover the qualified positive label
and shared soft-light instruction with accessibility semantics and enlarged text.

Cross-device/participant calibration and physical lighting acceptance: **pending**.
