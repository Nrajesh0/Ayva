// Synthetic device probe only. Never linked into the application.
#include "common.h"

int main(int argc, char** argv) {
    if (argc != 2 || chdir(argv[1])) return 2;
    char app[] = "run-probe";
    char local[] = "local";
    char bot[] = "bot=run";
    char language[] = "language=english";
    char users[] = "userlog=0";
    char server[] = "serverlog=0";
    char dictionary[] = "dict=350000";
    char facts[] = "fact=300000";
    char heap[] = "text=30000";
    char debug[] = "nodebug";
    char* parameters[] = {app, local, bot, language, users, server, dictionary, facts, heap, debug};
    if (InitSystem(10, parameters, argv[1], argv[1], argv[1])) return 3;
    char user[80];
    snprintf(user, sizeof(user), "run-smoke-%d", getpid());
    char callee[] = "run";
    const char* inputs[] = {"I prefer quiet evenings", "Crowds wear me out", "what could I try?", "actually outdoors", "what could I try?", "why that?"};
    const char* expected[] = {"quiet evening", "quieter setting", "reading", "outdoors", "walk", "crowds"};
    int failed = 0;
    for (unsigned i = 0; i < sizeof(inputs) / sizeof(inputs[0]); ++i) {
        char input[4001];
        // PerformChat stores this pointer globally and CloseSystem frees it.
        // Reuse the engine-owned allocation rather than passing a stack buffer.
        char* output = ourMainOutputBuffer;
        *output = 0;
        snprintf(input, sizeof(input), "%s", inputs[i]);
        int turn = PerformChat(user, callee, input, NULL, output);
        bool passed = turn >= 0 && strstr(output, expected[i]);
        printf("RUN_SMOKE turn=%u passed=%d\n", i + 1, passed);
        if (!passed) ++failed;
    }
    // Let Android report a native tombstone if teardown fails instead of hiding
    // the fault behind the engine's process-level signal handler.
    signal(SIGSEGV, SIG_DFL);
    CloseSystem();
    printf("RUN_SMOKE failures=%d closed=1\n", failed);
    return failed ? 4 : 0;
}
