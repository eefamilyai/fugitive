type Nullable<T> = T | null | undefined
declare function KtSingleton<T>(): T & (abstract new() => any);
export declare class WebEngine {
    constructor();
    catalog(): string;
    settingsForm(configJson: string): string;
    setSetting(configJson: string, key: string, valueJson: string): string;
    applyPreset(presetId: string): string;
    switchMode(configJson: string, modeId: string): string;
    resetConfig(configJson: string): string;
    validate(lobbyJson: string, locationsJson: string, participantIdsJson: string): string;
    playArea(lobbyJson: string, locationsJson: string, participantIdsJson: string): string;
    startGame(lobbyJson: string, locationsJson: string, participantIdsJson: string, now: number): string;
    refereeTick(gameJson: string, membersJson: string, locationsJson: string, actionsJson: string, hostId: string, now: number): string;
    recordLocations(locationsJson: string): void;
    clearHistory(): void;
    project(lobbyJson: string, locationsJson: string, selfId: string, now: number): string;
    stats(gameJson: string, trailsJson: string): string;
    formatClock(ms: number): string;
}
export declare namespace WebEngine {
    /** @deprecated $metadata$ is used for internal purposes, please don't use it in your code, because it can be removed at any moment */
    namespace $metadata$ {
        const constructor: abstract new () => WebEngine;
    }
}