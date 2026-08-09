# Monolith AI UI Generation 2

UI Generation 2 establishes deterministic landscape scene ownership for the Monolith core.

## Launch flow

1. Native Deterministic Startup Boundary (BIOS)
2. Dedicated `monolith-launch` House Dedmon Access scene
3. Command Chamber
4. Exclusive destination scenes: Chat, Archives, Dedmon Studio, Settings, Monolith Model, Voice Module, and RPG

The House Dedmon Access screen is no longer a body-level overlay. The historical `#ownerGate` exists only as a hidden compatibility anchor while the preserved command-deck graph initializes, then it is removed. The visible House Dedmon interface is created as a peer scene inside `#janeSceneHost` and uses the same single-scene routing authority as every other destination.

## Landscape geometry

`monolith_landscape_gen2.css` owns viewport geometry for the launch, command, and chat scenes. The portrait-era legacy CSS remains available only for preserved widget styling and Safe Base compatibility.

The Command Chamber uses a three-column landscape chassis:

- left: live Android telemetry
- center: holographic GLB projection stage, 55% target width
- right: destination and module controls

Chat uses a dedicated two-column conversation layout with a portrait/visual bay and independent response console.

## Failure containment

The scene bootstrap keeps a timed viewport watchdog active until the dedicated launch scene is mounted. If scene construction stalls, the initialization visibility lock is released rather than leaving the core on a permanent black viewport.

## CI requirements

The automated build rejects a package if any of the following regress:

- `monolith-launch` is missing
- the House Dedmon crest or access scene is missing
- the old House Dedmon overlay reappears in `index.html`
- fake biometric telemetry returns
- module overlay architecture returns
- generation-2 landscape CSS is missing
- required activities lose `sensorLandscape`
