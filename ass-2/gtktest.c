
#include <gtk/gtk.h>

static gboolean on_delete(
    GtkWidget *widget,
    GdkEvent  *event,
    gpointer  arg
);
static void on_destroy(
    GtkWidget *widget,
    gpointer  arg
);

int main(int argc, char **argv) {
    const int width  = 256;
    const int height = 256;
    
    GtkWidget *window;
    GtkWidget *image;
    guchar image_data[width * height * 3];
    GdkPixbuf *pixbuf;
    
    for (int i=0; i<width; i++)
    for (int j=0; j<height; j++) {
        int pix = i+j*width;
            
        int r = i ^ j;
        int g = (i*2) ^ (j*2);
        int b = (i*4) ^ (j*4);
        
        image_data[3*pix+0] = r;
        image_data[3*pix+1] = g;
        image_data[3*pix+2] = b;
    }
    
    gtk_init(&argc, &argv);
    window = gtk_window_new(GTK_WINDOW_TOPLEVEL);
    gtk_widget_set_usize(window, width, height);
    gtk_signal_connect(
        GTK_OBJECT(window),
        "delete-event",
        G_CALLBACK(on_delete),
        NULL
    );
    gtk_signal_connect(
        GTK_OBJECT(window),
        "destroy",
        G_CALLBACK(on_destroy),
        NULL
    );
    
    pixbuf = gdk_pixbuf_new_from_data(
        image_data,
        GDK_COLORSPACE_RGB,
        FALSE,
        8,
        width,
        height,
        width * 3,
        NULL,
        NULL
    );
    
    image = gtk_image_new_from_pixbuf(pixbuf);
    gtk_container_add(GTK_CONTAINER(window), image);
    
    gtk_widget_show(image);
    gtk_widget_show(window);
    
    gtk_main();
    
    return 0;
}

static gboolean on_delete(
    GtkWidget *widget,
    GdkEvent  *event,
    gpointer  data
)
{
    return FALSE;
}

static void on_destroy(
    GtkWidget *widget,
    gpointer  arg
)
{
    gtk_main_quit();
}

