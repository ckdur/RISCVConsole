// See LICENSE for license details.

#ifndef _RATONA_CODEC_H
#define _RATONA_CODEC_H

/* Register offsets */

#define CODEC_REG_OUT_L         0x00
#define CODEC_REG_OUT_R         0x04
#define CODEC_REG_IN_L          0x08
#define CODEC_REG_IN_R          0x0c
#define CODEC_REG_CTRL          0x10
#define CODEC_REG_STATUS        0x14

/* Fields */
#define CODEC_CTRL_WRITE_AUD_OUT (1UL << 0)
#define CODEC_CTRL_READ_AUD_IN (1UL << 1)

#define CODEC_CTRL_CLR_AUD_OUT (1UL << 8)
#define CODEC_CTRL_CLR_AUD_IN (1UL << 9)

#define CODEC_CTRL_INT_AUD_OUT (1UL << 16)
#define CODEC_CTRL_INT_AUD_IN (1UL << 17)

#define CODEC_STAT_AUD_OUT_ALLOW (1UL << 0)
#define CODEC_STAT_AUD_IN_AVAIL (1UL << 1)

#endif /* _RATONA_CODEC_H */
