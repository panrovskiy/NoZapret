#ifndef BYEDPI_MAIN_H
#define BYEDPI_MAIN_H

#include "byedpi/params.h"

#ifdef __cplusplus
extern "C" {
#endif

extern int server_fd;
int android_protect_tunnel_socket(int fd);
void reset_params(void);
void clear_params(char *line, char **argv);
int byedpi_main(int argc, char **argv);
void byedpi_stop(void);

#ifdef __cplusplus
}
#endif

#endif
