
#define _XOPEN_SOURCE // for crypt()

#include "fractal.h"

#include <stdbool.h>
#include <math.h>
#include <string.h>
#include <complex.h>
#include <unistd.h>
#include <stdio.h>

const int iter_max = 100;

const double center_x = -0.78;
const double center_y = 0.0;
const double delta = 2.5;

static double min_x;
static double max_x;
static double min_y;
static double max_y;

static double col_to_x(int col) { return (double)col/width*(max_x-min_x) + min_x; }
static double row_to_y(int row) { return (double)row/height*(max_y-min_y) + min_y; }

int calc_in(
    int col,
    int row
)
{
    min_x = center_x - delta/2;
    max_x = center_x + delta/2;
    min_y = center_y - delta/2;
    max_y = center_y + delta/2;
    
    double x = col_to_x(col);
    double y = row_to_y(row);
    
    complex double z0 = x + y*I;
    complex double z = z0;
    
    char in = true;
    
    for (int i=0; i<iter_max; i++) {
        if (cabs(z) > 16) {
            in = false;
            break;
        }
        
        z = z*z + z0;
    }
    
    return in;
}

void print_hash(int len, const char image_data[len]) {
    char nt_data[len];
    memcpy(nt_data, image_data, len);
    
    // The image data currently has many 0s. We need to make it
    // be a null-terminated string.
    for (int i=0; i<len; i++) {
        if (nt_data[i] == 0) {
            nt_data[i] = i & 0xFF;
        }
    }
    
    const char *hash = crypt(nt_data, "ab");
    printf("Hash: %s\n", hash);
}

