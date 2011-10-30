
/*
 * Generate a reference hash, and display an image.
 *
 */
#define _XOPEN_SOURCE // for crypt()
#include <gtk/gtk.h>
#include <complex.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

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

double x_to_col(double x) { return (int)round((x-min_x)/(max_x-min_x)*width); }
double y_to_row(double y) { return (int)round((y-min_y)/(max_y-min_y)*height); }

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

static void print_hash(int len, const unsigned char image_data[len]) {
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

static void collect_data(guchar *image_data) {
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

static gboolean on_delete(
    GtkWidget *widget, GdkEvent *event, gpointer data
) { return FALSE; }

static void on_destroy(
    GtkWidget *widget, gpointer arg
) { gtk_main_quit(); }

void draw_image(int argc, char **argv, guchar *image_data) {
    GtkWidget *window;
    GtkWidget *image;
    GdkPixbuf *pixbuf;
    
    gtk_init(&argc, &argv);
    window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_widget_set_usize(window, width, height);
    gtk_signal_connect(
        GTK_OBJECT(window), "delete-event",
        G_CALLBACK(on_delete), NULL
    );
    gtk_signal_connect(
        GTK_OBJECT(window), "destroy",
        G_CALLBACK(on_destroy), NULL
    );
    
    pixbuf = gdk_pixbuf_new_from_data(
        image_data, GDK_COLORSPACE_RGB,
        FALSE, 8, width, height,
        width * 3, NULL, NULL
    );
    
    image = gtk_image_new_from_pixbuf(pixbuf);
    gtk_container_add(GTK_CONTAINER(window), image);
    
    gtk_widget_show(image);
    gtk_widget_show(window);
    
    gtk_main();
}

int main(int argc, char **argv) {
    guchar image_data[width * height * 3];
    
    collect_data(image_data);
    print_hash(width*height*3, image_data);
    draw_image(argc, argv, image_data);
}

