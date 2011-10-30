
/*
 * Generate a reference bit vector
 *
 */
#include <complex.h>
#include <math.h>
#include <stdlib.h>
#include <stdbool.h>
#include <stdio.h>

#define TRUE true
#define FALSE false

#define width  800
#define height 800

const int iter_max = 100;

const double center_x = -0.78;
const double center_y = 0.0;
const double delta = 2.5;

double min_x;
double max_x;
double min_y;
double max_y;

double col_to_x(int col) { return (double)col/width*(max_x-min_x) + min_x; }
double row_to_y(int row) { return (double)row/height*(max_y-min_y) + min_y; }

static int calc_in(
    int col,
    int row
)
{
    double x = col_to_x(col);
    double y = row_to_y(row);
    
    complex double z0 = x + y*I;
    complex double z = z0;
    
    char in = TRUE;
    
    for (int i=0; i<iter_max; i++) {
        if (cabs(z) > 16) {
            in = FALSE;
            break;
        }
        
        z = z*z + z0;
    }
    
    return in;
}

static void collect_data(char *image_data) {
    min_x = center_x - delta/2;
    max_x = center_x + delta/2;
    min_y = center_y - delta/2;
    max_y = center_y + delta/2;
    
    for (int i=0; i<height; i++)
    for (int j=0; j<width; j++) {
        int pix = i*width+j;
        char in = calc_in(j, i);
        
        image_data[3*pix+0] = in-1;
        image_data[3*pix+1] = in-1;
        image_data[3*pix+2] = in-1;
    }
}

static void write_reference(char *image_data, const char *filename) {
    FILE *ref = fopen(filename, "wb");
    if (ref == NULL) {
        perror("Failed to open reference file");
        exit(1);
    }
    
    for (int i=0; i<width*height*3; i++) {
        fwrite(image_data+i, sizeof(char), 1, ref);
    }
    
    fflush(ref);
    fclose(ref);
}

int main(int argc, char **argv) {
    if (argc <= 1) {
        fprintf(stderr, "usage: %s <ref-file>\n", argv[0]);
        return 1;
    }
    
    char image_data[width * height * 3];
    
    collect_data(image_data);
    write_reference(image_data, argv[1]);
}

