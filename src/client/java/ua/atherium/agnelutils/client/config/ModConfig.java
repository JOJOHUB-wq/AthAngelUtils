package ua.atherium.agnelutils.client.config;

public final class ModConfig {
    public String password = "0707";
    public boolean autoLogin = true;
    public long loginDelayMs = 2500;
    public int loginMaxRetries = 2;
    public long loginRetryDelayMs = 3000;

    public boolean autoReconnect = true;
    public int reconnectDelaySeconds = 5;
    public int reconnectBaseDelaySec = 5;
    public int reconnectMaxDelaySec = 300;
    public int reconnectMaxFails = 15;
    public int maxReconnectAttempts = 0;
    public boolean onlyAngelGrief = true;
    public String serverHostContains = "angelgrief";

    public boolean captchaAssist = true;
    public boolean captchaSavePng = true;
    public boolean captchaSound = true;

    public boolean autoHub = true;
    public long hubCompassDelayMs = 1500;
    public String[] anarchyKeywords = {"анарх", "1.21"};
    public String[] hubTitleKeywords = {"выбор режима"};
    public int anarchySlot = 8;
    public int hubMenuWaitMs = 8000;

    public boolean minerEnabled = false;
    public boolean capslockGate = true;
    public String warpMineCommand = "warp mine";
    public int mineRadius = 4;
    public int minPickaxeDurability = 60;
    public long mineTickDelayMs = 150;
    public boolean minerAutoSellAll = false;
    public boolean noFly = true;

    public int repairThreshold = 400;
    public String fixCommand = "fix";
    public long fixCooldownMs = 300000;
    public boolean repairUseFix = true;
    public boolean repairUseNuggets = true;
    public boolean repairUseBubbleCraft = true;
    public int glassBottleLimit = 64;
    public boolean glassExcessDumpAll = true;
    public String craftsCommand = "crafts";
    public String craftCommand = "craft";
    public String[] workbenchTitleKeywords = {"верстак", "craft"};
    public long repairAbortCooldownMs = 60000;
    public String[] bubbleTitleKeywords = {"пузыр", "опыта"};
    public int bubbleCraftSlot = 25;
    public long repairClickDelayMs = 700;
    public int repairMaxClicks = 40;
    public long throwDelayMs = 400;
    public int throwBatchCheck = 8;
    public int throwMaxPerCycle = 128;

    public boolean debugScreens = true;

    public boolean captchaDumpMaps = true;
    public boolean captchaAutoSend = true;
    public int captchaMaxAutoAttempts = 2;
    public double captchaMinScore = 0.45;
    public double captchaMinAvg = 0.55;

    public String buyerCommand = "buyer";
    public String[] buyerTitleKeywords = {"автоскупка"};
    public boolean buyerAutoEnable = false;

    public boolean lookAtTargets = true;
    public long stuckTimeoutMs = 4000;
    public long noTargetStepMs = 3000;
    public long stuckStepMs = 500;
    public int maxStucksBeforeRewarp = 3;

    public long sellTimeoutMin = 5;
    public long rewarpIntervalMin = 25;
    public long walkTimeoutMs = 25000;
    public long denyRewarpCooldownMs = 20000;

    public boolean farmMode = false;
    public int farmCycles = 30;
    public long farmSolveWaitSec = 60;
    public long farmDelaySec = 8;
    public String farmPrefix = "slavamarlowww";
    public int farmStartIndex = 1;
    public String farmPass = "";

    public boolean mineBoxEnforce = true;
    public int[] mineBox = {-12, 74, 18, 14, 89, 48};
}
