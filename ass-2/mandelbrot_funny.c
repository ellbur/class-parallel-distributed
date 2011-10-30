
/*
 * Sequential Mandelbrot program
 */

#include <gtk/gtk.h>
#include <complex.h>
#include <math.h>

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

char in_buffer[width][height];

static int calc_color(
    int col,
    int row
)
{
    int hit_cols[iter_max];
    int hit_rows[iter_max];
    int i;
    char in;
    
    double x = col_to_x(col);
    double y = row_to_y(row);
    
    complex double z0 = x + y*I;
    complex double z = z0;
    
    int vote = 0;
    char sure = 0;
    
    for (i=0; i<iter_max; i++) {
        int icol = x_to_col(creal(z));
        int irow = y_to_row(cimag(z));
        
        hit_cols[i] = icol;
        hit_rows[i] = irow;
        
        if (0<=icol && icol<width && 0<=irow && irow < height)
            vote += in_buffer[icol][irow];
        
        if (vote <= -30) {
            in = FALSE;
            sure = 0;
            goto done;
        }
        if (vote >= +30) {
            in=TRUE;
            sure = 0;
            goto done;
        }
        
        if (cabs(z) > 16) {
            in = FALSE;
            sure = 0;
            goto done;
        }
        
        z = z*z + z0;
    }
    in = TRUE;
    sure = 1;
    
    done: ;
    
    for (int j=0; j<i; j++) {
        int icol = hit_cols[j];
        int irow = hit_rows[j];
        
        if (0<=icol && icol<width && 0<=irow && irow<height) {
            char cur = in_buffer[icol][irow];
            
            if      (cur < 0  && sure > 0)   in_buffer[icol][irow] = 0;
            else if (cur <= 0 && sure < cur) in_buffer[icol][irow] = sure;
            else if (cur >= 0 && sure > cur) in_buffer[icol][irow] = sure;
        }
    }
    
    return in ? 0 : -1;
}

static gboolean on_delete(
    GtkWidget *widget, GdkEvent *event, gpointer arg
);
static void on_destroy(
    GtkWidget *widget, gpointer arg
);

int main(int argc, char **argv) {
    GtkWidget *window;
    GtkWidget *image;
    guchar image_data[width * height * 3];
    GdkPixbuf *pixbuf;
    
    min_x = center_x - delta/2;
    max_x = center_x + delta/2;
    min_y = center_y - delta/2;
    max_y = center_y + delta/2;
    
    for (int i=0; i<width; i++)
    for (int j=0; j<height; j++) {
        int pix = i+j*width;
        int color = calc_color(i, j);
            
        image_data[3*pix+0] = (color>>16) & 0xFF;
        image_data[3*pix+1] = (color>>8)  & 0xFF;
        image_data[3*pix+2] = (color>>0)  & 0xFF;
    }
    
    //if (min_x < 0) return 0;
    
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
    
    return 0;
}

static gboolean on_delete(
    GtkWidget *widget, GdkEvent *event, gpointer data
) { return FALSE; }

static void on_destroy(
    GtkWidget *widget, gpointer arg
) { gtk_main_quit(); }

