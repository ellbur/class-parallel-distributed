#include "mpi.h"
#include <stdio.h>

int main(int argc, char** argv)
{
  int MyProc, size, tag = 0;
  int send_proc = 0, recv_proc = 0;
  char msg[12]="Hello World";
  char msg_recpt[12]="I am alone!";
  MPI_Status status;

  MPI_Init(&argc, &argv);
  MPI_Comm_rank(MPI_COMM_WORLD, &MyProc);
  MPI_Comm_size(MPI_COMM_WORLD, &size);

  printf("Process # %d started \n", MyProc);
  printf("Proc #%d: %s \n", MyProc, msg_recpt) ;
  MPI_Barrier(MPI_COMM_WORLD);


 for (send_proc = 0; send_proc < size; send_proc++)
  {
    if (send_proc != MyProc)
    {
      printf("Proc #%d sending message to Proc #%d: %s \n", MyProc, send_proc, msg);
      MPI_Send(&msg, 12, MPI_CHAR, send_proc, tag, MPI_COMM_WORLD);
    }
  }

  for (recv_proc = 0; recv_proc < size; recv_proc++)
  {
    if (recv_proc != MyProc)
    {
      MPI_Recv(&msg_recpt, 12, MPI_CHAR, recv_proc, tag, MPI_COMM_WORLD, &status);
      printf("Proc #%d received message from Proc #%d: %s \n", MyProc, recv_proc, msg_recpt);
    }
   }


  MPI_Finalize();


  }
