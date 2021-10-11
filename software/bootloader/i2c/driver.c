#include "platform.h"
#include "print.h"
#include "time.h"
#include "encoding.h"
#include "i2c.h"
#include <stddef.h>

// NOTE: Re-structured from sifive-metal. We do not support the sifive-metal

/* Prescaler max value */
#define METAL_I2C_PRESCALE_MAX 0xFFFF
/* Macros to access registers */
#define METAL_I2C_REG(offset) ((base + offset))
#define METAL_I2C_REGB(offset) (*(volatile uint8_t *)METAL_I2C_REG(offset))
#define METAL_I2C_REGW(offset) (*(volatile uint32_t *)METAL_I2C_REG(offset))
#define METAL_I2C_INIT_OK 1
#define METAL_I2C_RET_OK 0
#define METAL_I2C_RET_ERR -1

/* Driver console logging */
#define METAL_I2C_LOG(x) print_str(x)

#define METAL_SIFIVE_I2C_INSERT_STOP(stop_flag) ((stop_flag & 0x01UL) << 6)
#define METAL_SIFIVE_I2C_INSERT_RW_BIT(addr, rw)                               \
    ((addr & 0x7FUL) << 1 | (rw & 0x01UL))
#define METAL_SIFIVE_I2C_GET_PRESCALER(baud)                                   \
    ((clock_rate / (baud_rate * 5)) - 1)

/* Timeout macros for register status checks */
#define METAL_I2C_RXDATA_TIMEOUT 1
#define METAL_I2C_TIMEOUT_RESET(timeout)                                       \
    timeout = metal_time() + METAL_I2C_RXDATA_TIMEOUT
#define METAL_I2C_TIMEOUT_CHECK(timeout)                                       \
    if (metal_time() > timeout) {                                              \
        METAL_I2C_LOG("I2C timeout error.\n");                                 \
        return METAL_I2C_RET_ERR;                                              \
    }
#define METAL_I2C_REG_CHECK(exp, timeout)                                      \
    while (exp) {                                                              \
        METAL_I2C_TIMEOUT_CHECK(timeout)                                       \
    }

void i2c0_init(void *gi2c,
                      unsigned int baud_rate,
                      metal_i2c_mode_t mode) {
  METAL_I2C_LOG("i2c0_init:\n");
  if ((gi2c != NULL)) {
    /* 1: Master 0: Slave */
    if (mode == METAL_I2C_MASTER) {
      /* Set requested baud rate */
      if (i2c0_set_baud_rate(gi2c, baud_rate) == METAL_I2C_RET_OK) {
        /* Init ok*/
      }
    } else {
      /* Nothing to do. slave mode not supported */
    }
  }
}

#define METAL_SIFIVE_I2C_GET_PRESCALER(baud) ((clock_rate / (baud_rate * 5)) - 1)
int i2c0_set_baud_rate(void *gi2c,
                              unsigned int baud_rate) {
  METAL_I2C_LOG("i2c0_set_baud_rate:\n");
  int ret = METAL_I2C_RET_ERR;
  unsigned long base = (unsigned long)gi2c;

  if ( (gi2c != NULL)) {
    long clock_rate = F_CLK;

    /* Calculate prescaler value */
    long prescaler = METAL_SIFIVE_I2C_GET_PRESCALER(baud_rate);

    if (!(prescaler > METAL_I2C_PRESCALE_MAX) || (prescaler < 0)) {
      /* Set pre-scaler value */
      METAL_I2C_REGB(I2C_REG_CONTROL) &= ~I2C_CONTROL_EN;
      METAL_I2C_REGB(I2C_REG_PRESCALE_LOW) = prescaler & 0xFF;
      METAL_I2C_REGB(I2C_REG_PRESCALE_HIGH) = (prescaler >> 8) & 0xFF;
      METAL_I2C_REGB(I2C_REG_CONTROL) |= I2C_CONTROL_EN;

      //i2c->baud_rate = baud_rate;
      ret = METAL_I2C_RET_OK;
    } else {
      /* Out of range value, return error */
      METAL_I2C_LOG("I2C Set baud failed.\n");
    }
  } else {
    METAL_I2C_LOG("I2C Set baud failed.\n");
  }

  return ret;
}

int i2c0_write_addr(unsigned long base,
                           unsigned int addr,
                           unsigned char rw_flag) {
  METAL_I2C_LOG("i2c0_write_addr:\n");
    uint32_t timeout;
    int ret = METAL_I2C_RET_OK;
    /* Reset timeout */
    METAL_I2C_TIMEOUT_RESET(timeout);

    /* Check if any transfer is in progress */
    METAL_I2C_REG_CHECK(
        (METAL_I2C_REGB(I2C_REG_STATUS) & I2C_STATUS_TIP),
        timeout);

    /* Set transmit register to given address with read/write flag */
    METAL_I2C_REGB(I2C_REG_TRANSMIT) =
        METAL_SIFIVE_I2C_INSERT_RW_BIT(addr, rw_flag);

    /* Set start flag to trigger the address transfer */
    METAL_I2C_REGB(I2C_REG_COMMAND) =
        I2C_CMD_WRITE | I2C_CMD_START;
    /* Reset timeout */
    METAL_I2C_TIMEOUT_RESET(timeout);

    /* Check for transmit completion */
    METAL_I2C_REG_CHECK(
        (METAL_I2C_REGB(I2C_REG_STATUS) & I2C_STATUS_TIP),
        timeout);

    /* Check for ACK from slave */
    if ((METAL_I2C_REGB(I2C_REG_STATUS) & I2C_STATUS_RXACK)) {
        /* No ACK, return error */
        METAL_I2C_LOG("I2C RX ACK failed.\n");
        ret = METAL_I2C_RET_ERR;
    }

    return ret;
}

int i2c0_write(void *gi2c,
                      unsigned int addr, unsigned int len,
                      unsigned char buf[],
                      metal_i2c_stop_bit_t stop_bit) {
  METAL_I2C_LOG("i2c0_write:\n");
  uint8_t command;
  uint32_t timeout;
  int ret;
  unsigned long base = (unsigned long)gi2c;
  unsigned int i;

  if ((gi2c != NULL)) {

    /* Send address over I2C bus, current driver supports only 7bit
     * addressing */
    ret = i2c0_write_addr(base, addr, I2C_WRITE);

    if (ret != METAL_I2C_RET_OK) {
      /* Write address failed */
      METAL_I2C_LOG("I2C Address Write failed.\n");
    } else {
      /* Set command flags */
      command = I2C_CMD_WRITE;

      for (i = 0; i < len; i++) {
        /* Copy into transmit register */
        METAL_I2C_REGB(I2C_REG_TRANSMIT) = buf[i];

        /* for last byte transfer, check if stop condition is requested
         */
        if (i == (len - 1)) {
            command |= METAL_SIFIVE_I2C_INSERT_STOP(stop_bit);
        }
        /* Write command register */
        METAL_I2C_REGB(I2C_REG_COMMAND) = command;
        /* Reset timeout */
        METAL_I2C_TIMEOUT_RESET(timeout);

        /* Check for transfer completion */
        METAL_I2C_REG_CHECK((METAL_I2C_REGB(I2C_REG_STATUS) &
                             I2C_STATUS_TIP),
                            timeout);

        /* Check for ACK from slave */
        if ((METAL_I2C_REGB(I2C_REG_STATUS) &
             I2C_STATUS_RXACK)) {
          /* No ACK, return error */
          METAL_I2C_LOG("I2C RX ACK failed.\n");
          ret = METAL_I2C_RET_ERR;
          break;
        }
      }
    }

  } else {
    /* I2C device not initialized, return error */
    METAL_I2C_LOG("I2C device not initialized.\n");
    ret = METAL_I2C_RET_ERR;
  }

  return ret;
}
int i2c0_read(void *gi2c,
                     unsigned int addr, unsigned int len,
                     unsigned char buf[],
                     metal_i2c_stop_bit_t stop_bit) {
  METAL_I2C_LOG("i2c0_read:\n");
  int ret;
  uint8_t command;
  uint32_t timeout;
  unsigned int i;
  unsigned long base = (unsigned long)gi2c;

  if ((gi2c != NULL)) {

    /* Send address over I2C bus, current driver supports only 7bit
     * addressing */
    ret = i2c0_write_addr(base, addr, I2C_READ);

    if (ret != METAL_I2C_RET_OK) {
        /* Write address failed */
        METAL_I2C_LOG("I2C Read failed.\n");
    } else {
      /* Set command flags */
      command = I2C_CMD_READ;

      for (i = 0; i < len; i++) {
        /* check for last transfer */
        if (i == (len - 1)) {
          /* Set NACK to end read, if requested generate STOP
           * condition */
          command |= (I2C_CMD_ACK |
                      METAL_SIFIVE_I2C_INSERT_STOP(stop_bit));
        }
        /* Write command register */
        METAL_I2C_REGB(I2C_REG_COMMAND) = command;
        /* Reset timeout */
        METAL_I2C_TIMEOUT_RESET(timeout);

        /* Wait for the read to complete */
        METAL_I2C_REG_CHECK((METAL_I2C_REGB(I2C_REG_STATUS) &
                             I2C_STATUS_TIP),
                            timeout);
        /* Store the received byte */
        buf[i] = METAL_I2C_REGB(I2C_REG_TRANSMIT);
      }
    }
  } else {
    /* I2C device not initialized, return error */
    METAL_I2C_LOG("I2C device not initialized.\n");
    ret = METAL_I2C_RET_ERR;
  }

  return ret;
}

int i2c0_transfer(void *gi2c, unsigned int addr,
                         unsigned char txbuf[], unsigned int txlen,
                         unsigned char rxbuf[], unsigned int rxlen) {
  uint8_t command;
  uint32_t timeout;
  int ret;
  unsigned int i;
  unsigned long base = (unsigned long)gi2c;

  if ((gi2c != NULL)) {
    if (txlen) {
      /* Set command flags */
      command = I2C_CMD_WRITE;
      /* Send address over I2C bus, current driver supports only 7bit
       * addressing */
      ret = i2c0_write_addr(base, addr, I2C_WRITE);

      if (ret != METAL_I2C_RET_OK) {
        /* Write address failed */
        METAL_I2C_LOG("I2C Write failed.\n");
        return ret;
      }
      for (i = 0; i < txlen; i++) {
        /* Copy into transmit register */
        METAL_I2C_REGB(I2C_REG_TRANSMIT) = txbuf[i];

        if (i == (txlen - 1) && (rxlen == 0)) {
          /* Insert stop condition to end transfer */
          command |= I2C_CMD_STOP;
        }
        /* Write command register */
        METAL_I2C_REGB(I2C_REG_COMMAND) = command;
        /* Reset timeout */
        METAL_I2C_TIMEOUT_RESET(timeout);

        /* Check for transfer completion. */
        METAL_I2C_REG_CHECK((METAL_I2C_REGB(I2C_REG_STATUS) &
                             I2C_STATUS_TIP),
                            timeout);

        /* Check for ACK from slave. */
        if ((METAL_I2C_REGB(I2C_REG_STATUS) &
             I2C_STATUS_RXACK)) {
          /* No ACK, return error */
          METAL_I2C_LOG("I2C RX ACK failed.\n");
          ret = METAL_I2C_RET_ERR;
          break;
        }
      }
    }
    if (rxlen) {
      command = I2C_CMD_READ; /* Set command flags */
      /* Send address over I2C bus, current driver supports only 7bit
       * addressing */
      ret = i2c0_write_addr(base, addr, I2C_READ);

      if (ret != METAL_I2C_RET_OK) {
        /* Return error */
        METAL_I2C_LOG("I2C Read failed.\n");
        return ret;
      }
      for (i = 0; i < rxlen; i++) {
        /* check for last transfer */
        if (i == (rxlen - 1)) {
          /* Set NACK to end read, generate STOP condition */
          command |= (I2C_CMD_ACK | I2C_CMD_STOP);
        }
        /* Write command register */
        METAL_I2C_REGB(I2C_REG_COMMAND) = command;
        /* Reset timeout */
        METAL_I2C_TIMEOUT_RESET(timeout);

        /* Wait for the read to complete */
        METAL_I2C_REG_CHECK((METAL_I2C_REGB(I2C_REG_STATUS) &
                             I2C_STATUS_TIP),
                            timeout);
        /* Store the received byte */
        rxbuf[i] = METAL_I2C_REGB(I2C_REG_TRANSMIT);
      }
    }
  } else {
    /* I2C device not initialized, return error */
    METAL_I2C_LOG("I2C device not initialized.\n");
    ret = METAL_I2C_RET_ERR;
  }

  return ret;
}

