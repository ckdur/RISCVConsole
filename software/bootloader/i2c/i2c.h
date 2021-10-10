#ifndef METAL__I2C_H
#define METAL__I2C_H

/*! @brief Enums to enable/disable stop condition. */
typedef enum {
    METAL_I2C_STOP_DISABLE = 0,
    METAL_I2C_STOP_ENABLE = 1
} metal_i2c_stop_bit_t;

/*! @brief Enums to set up I2C device modes. */
typedef enum { METAL_I2C_SLAVE = 0, METAL_I2C_MASTER = 1 } metal_i2c_mode_t;

void i2c0_init(void *gi2c,
                      unsigned int baud_rate,
                      metal_i2c_mode_t mode);

int i2c0_set_baud_rate(void *gi2c,
                              unsigned int baud_rate);

int i2c0_write(void *i2c,
                      unsigned int addr, unsigned int len,
                      unsigned char buf[],
                      metal_i2c_stop_bit_t stop_bit);

int i2c0_read(void *i2c,
                     unsigned int addr, unsigned int len,
                     unsigned char buf[],
                     metal_i2c_stop_bit_t stop_bit);

int i2c0_transfer(void *i2c, unsigned int addr,
                         unsigned char txbuf[], unsigned int txlen,
                         unsigned char rxbuf[], unsigned int rxlen);

#endif
