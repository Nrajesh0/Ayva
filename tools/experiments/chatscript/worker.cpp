// One bounded, stateless conversation classification per process. No app callbacks.
#include "common.h"
#include <sys/resource.h>

int main(int argc, char** argv) {
    if (argc != 3) return 2;
    // Upstream's diagnostic writer recursively reports missing log directories.
    // Check the caller-created workspace before initialization can open logs.
    const char* folders[] = {"LOGS", "USERS", "TMP"};
    for (unsigned i = 0; i < 3; ++i) {
        char path[4096];
        if (snprintf(path, sizeof(path), "%s/%s", argv[2], folders[i]) >= (int)sizeof(path) || access(path, W_OK)) return 2;
    }
    struct rlimit cpu = {6, 6};
    setrlimit(RLIMIT_CPU, &cpu);
    char app[] = "run-dialogue";
    char local[] = "local";
    char bot[] = "bot=run";
    char language[] = "language=english";
    char users[] = "userlog=0";
    char server[] = "serverlog=0";
    char bugs[] = "nobuglog";
    char dictionary[] = "dict=350000";
    char facts[] = "fact=300000";
    char heap[] = "text=30000";
    char debug[] = "nodebug";
    char cache[] = "cache=10x0";
    char* parameters[] = {app, local, bot, language, users, server, bugs, dictionary, facts, heap, debug, cache};
    if (InitSystem(12, parameters, argv[1], argv[1], argv[2])) return 3;
    // Initialization may generate public dictionary caches. Once user text can enter
    // the process, prohibit all regular-file writes, including unexpected bug logs.
    struct rlimit files = {0, 0};
    setrlimit(RLIMIT_FSIZE, &files);
    signal(SIGXFSZ, SIG_IGN);
    char input[4002] = {};
    if (!fgets(input, sizeof(input), stdin)) return 4;
    size_t length = strlen(input);
    if (length > 4000) return 4;
    while (length && (input[length - 1] == '\n' || input[length - 1] == '\r')) input[--length] = 0;
    if (!length || input[0] == ':' || input[0] == '/' || strchr(input, '\n')) return 4;
    char user[] = "run-transient";
    char callee[] = "run";
    char* output = ourMainOutputBuffer;
    *output = 0;
    if (PerformChat(user, callee, input, NULL, output) < 0) return 5;
    char code[80] = {};
    unsigned n = 0;
    while (output[n] && n < sizeof(code) - 1 && output[n] >= 'A' && output[n] <= 'Z') { code[n] = output[n]; ++n; }
    if (!n || (output[n] && output[n] != ' ' && output[n] != '\r' && output[n] != '\n')) return 5;
    CloseSystem();
    printf("RUNNATIVE1:%s\n", code);
    return 0;
}
