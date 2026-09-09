/* Minimal config.h for building zinnia with the Android NDK.
   Replaces the autotools-generated one; only the macros zinnia actually
   reads are defined. */
#define PACKAGE "zinnia"
#define VERSION "0.06"

#define HAVE_FCNTL_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_MMAN_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_UNISTD_H 1
#define HAVE_MMAP 1
