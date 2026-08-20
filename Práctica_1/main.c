#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Estructura para representar un proceso
typedef struct {
    char id[10];
    int tiempoLlegada;    // AT
    int rafagaCpu;        // BT
    int tiempoFinalizacion; // CT
    int tiempoRetorno;    // TAT
    int tiempoEspera;     // WT
    int tiempoRespuesta;  // RT
    int tiempoInicio;     // Momento en que recibe CPU por primera vez
    int completado;       // Bandera (0 = falso, 1 = verdadero)
} Proceso;

// Estructura para almacenar bloques del diagrama de Gantt
typedef struct {
    char procesoId[10];
    int inicio;
    int fin;
} BloqueGantt;

// Función auxiliar para ordenar los procesos por tiempo de llegada (para FCFS)
int compararPorLlegada(const void *a, const void *b) {
    Proceso *p1 = (Proceso *)a;
    Proceso *p2 = (Proceso *)b;
    return p1->tiempoLlegada - p2->tiempoLlegada;
}

// Función auxiliar para ordenar los procesos por ID original
int compararPorId(const void *a, const void *b) {
    Proceso *p1 = (Proceso *)a;
    Proceso *p2 = (Proceso *)b;
    return strcmp(p1->id, p2->id);
}

int main() {
    int n, opcion;

    printf("=========================================\n");
    printf("   SIMULADOR DE PLANIFICACION DE CPU     \n");
    printf("=========================================\n");

    // Ingresar número de procesos con manejo de errores
    while (1) {
        printf("Ingrese el numero de procesos (mayor a 0): ");
        if (scanf("%d", &n) == 1 && n > 0) {
            break;
        } else {
            printf("Error: Ingrese un valor entero valido mayor que cero.\n");
            while(getchar() != '\n');
        }
    }

    Proceso *procesos = (Proceso *)malloc(n * sizeof(Proceso));
    Proceso *listaSimulacion = (Proceso *)malloc(n * sizeof(Proceso));

    // Ingresar datos de cada proceso
    for (int i = 0; i < n; i++) {
        printf("\n--- Datos del Proceso P%d ---\n", i + 1);
        sprintf(procesos[i].id, "P%d", i + 1);
        
        while (1) {
            printf("Tiempo de llegada (AT) para P%d (>= 0): ", i + 1);
            if (scanf("%d", &procesos[i].tiempoLlegada) == 1 && procesos[i].tiempoLlegada >= 0) {
                break;
            } else {
                printf("Error: El tiempo de llegada no puede ser negativo.\n");
                while(getchar() != '\n');
            }
        }

        while (1) {
            printf("Rafaga de CPU (BT) para P%d (> 0): ", i + 1);
            if (scanf("%d", &procesos[i].rafagaCpu) == 1 && procesos[i].rafagaCpu > 0) {
                break;
            } else {
                printf("Error: La rafaga debe ser mayor que cero.\n");
                while(getchar() != '\n');
            }
        }

        procesos[i].tiempoFinalizacion = 0;
        procesos[i].tiempoRetorno = 0;
        procesos[i].tiempoEspera = 0;
        procesos[i].tiempoRespuesta = -1;
        procesos[i].tiempoInicio = -1;
        procesos[i].completado = 0;
    }

    // Copiar a la lista de simulación y mantener una copia original para el orden de impresión
    for (int i = 0; i < n; i++) {
        listaSimulacion[i] = procesos[i];
    }

    // Seleccionar algoritmo
    while (1) {
        printf("\nSeleccione el algoritmo de planificacion:\n");
        printf("1. FCFS (First-Come, First-Served)\n");
        printf("2. SJF (Shortest Job First - No expropiativo)\n");
        printf("Ingrese su opcion (1 o 2): ");
        if (scanf("%d", &opcion) == 1 && (opcion == 1 || opcion == 2)) {
            break;
        } else {
            printf("Error: Opcion no valida. Seleccione 1 o 2.\n");
            while(getchar() != '\n');
        }
    }

    BloqueGantt *gantt = (BloqueGantt *)malloc(100 * sizeof(BloqueGantt));
    int totalBloquesGantt = 0;
    int tiempoActual = 0;

    // Ejecución de Algoritmos
    if (opcion == 1) {
        // --- FCFS ---
        qsort(listaSimulacion, n, sizeof(Proceso), compararPorLlegada);

        for (int i = 0; i < n; i++) {
            if (tiempoActual < listaSimulacion[i].tiempoLlegada) {
                tiempoActual = listaSimulacion[i].tiempoLlegada;
            }
            listaSimulacion[i].tiempoInicio = tiempoActual;
            listaSimulacion[i].tiempoRespuesta = listaSimulacion[i].tiempoInicio - listaSimulacion[i].tiempoLlegada;
            
            int inicioBloque = tiempoActual;
            tiempoActual += listaSimulacion[i].rafagaCpu;
            listaSimulacion[i].tiempoFinalizacion = tiempoActual;
            
            listaSimulacion[i].tiempoRetorno = listaSimulacion[i].tiempoFinalizacion - listaSimulacion[i].tiempoLlegada;
            listaSimulacion[i].tiempoEspera = listaSimulacion[i].tiempoRetorno - listaSimulacion[i].rafagaCpu;
            
            strcpy(gantt[totalBloquesGantt].procesoId, listaSimulacion[i].id);
            gantt[totalBloquesGantt].inicio = inicioBloque;
            gantt[totalBloquesGantt].fin = tiempoActual;
            totalBloquesGantt++;
        }

    } else {
        // --- SJF (Non-Preemptive / No expropiativo) ---
        int completados = 0;

        while (completados < n) {
            int indiceSiguiente = -1;
            int menorRafaga = 999999;

            for (int i = 0; i < n; i++) {
                if (!listaSimulacion[i].completado && listaSimulacion[i].tiempoLlegada <= tiempoActual) {
                    if (listaSimulacion[i].rafagaCpu < menorRafaga) {
                        menorRafaga = listaSimulacion[i].rafagaCpu;
                        indiceSiguiente = i;
                    }
                }
            }

            if (indiceSiguiente == -1) {
                int minAt = 999999;
                for (int i = 0; i < n; i++) {
                    if (!listaSimulacion[i].completado && listaSimulacion[i].tiempoLlegada < minAt) {
                        minAt = listaSimulacion[i].tiempoLlegada;
                    }
                }
                tiempoActual = minAt;
            } else {
                if (listaSimulacion[indiceSiguiente].tiempoInicio == -1) {
                    listaSimulacion[indiceSiguiente].tiempoInicio = tiempoActual;
                    listaSimulacion[indiceSiguiente].tiempoRespuesta = listaSimulacion[indiceSiguiente].tiempoInicio - listaSimulacion[indiceSiguiente].tiempoLlegada;
                }

                int inicioBloque = tiempoActual;
                tiempoActual += listaSimulacion[indiceSiguiente].rafagaCpu;
                listaSimulacion[indiceSiguiente].tiempoFinalizacion = tiempoActual;
                listaSimulacion[indiceSiguiente].tiempoRetorno = listaSimulacion[indiceSiguiente].tiempoFinalizacion - listaSimulacion[indiceSiguiente].tiempoLlegada;
                listaSimulacion[indiceSiguiente].tiempoEspera = listaSimulacion[indiceSiguiente].tiempoRetorno - listaSimulacion[indiceSiguiente].rafagaCpu;
                listaSimulacion[indiceSiguiente].completado = 1;
                completados++;

                strcpy(gantt[totalBloquesGantt].procesoId, listaSimulacion[indiceSiguiente].id);
                gantt[totalBloquesGantt].inicio = inicioBloque;
                gantt[totalBloquesGantt].fin = tiempoActual;
                totalBloquesGantt++;
            }
        }
        qsort(listaSimulacion, n, sizeof(Proceso), compararPorId);
    }

    // Sincronizar los resultados calculados de vuelta al arreglo original para mostrarlos ordenados por ID (P1, P2...)
    for (int i = 0; i < n; i++) {
        for (int j = 0; j < n; j++) {
            if (strcmp(procesos[i].id, listaSimulacion[j].id) == 0) {
                strcpy(procesos[i].id, listaSimulacion[j].id);
                procesos[i].tiempoLlegada = listaSimulacion[j].tiempoLlegada;
                procesos[i].rafagaCpu = listaSimulacion[j].rafagaCpu;
                procesos[i].tiempoFinalizacion = listaSimulacion[j].tiempoFinalizacion;
                procesos[i].tiempoRetorno = listaSimulacion[j].tiempoRetorno;
                procesos[i].tiempoEspera = listaSimulacion[j].tiempoEspera;
                procesos[i].tiempoRespuesta = listaSimulacion[j].tiempoRespuesta;
            }
        }
    }

    // Mostrar Resultados en Tabla
    printf("\n=================================================================\n");
    printf("               RESULTADOS FINALES                                \n");
    printf("=================================================================\n");
    printf("%-10s | %-5s | %-5s | %-5s | %-5s | %-5s | %-5s\n", "Proceso", "AT", "BT", "CT", "TAT", "WT", "RT");
    printf("-----------------------------------------------------------------\n");

    double sumaWt = 0;
    double sumaTat = 0;

    for (int i = 0; i < n; i++) {
        sumaWt += procesos[i].tiempoEspera;
        sumaTat += procesos[i].tiempoRetorno;
        printf("%-10s | %-5d | %-5d | %-5d | %-5d | %-5d | %-5d\n",
                procesos[i].id,
                procesos[i].tiempoLlegada,
                procesos[i].rafagaCpu,
                procesos[i].tiempoFinalizacion,
                procesos[i].tiempoRetorno,
                procesos[i].tiempoEspera,
                procesos[i].tiempoRespuesta);
    }

    double promedioWt = sumaWt / n;
    double promedioTat = sumaTat / n;

    printf("-----------------------------------------------------------------\n");
    printf("Tiempo de Espera Promedio (WT Prom): %.2f\n", promedioWt);
    printf("Tiempo de Retorno Promedio (TAT Prom): %.2f\n", promedioTat);

    // Mostrar Diagrama de Gantt en formato de matriz
    int tiempoMaximo = 0;
    for (int i = 0; i < totalBloquesGantt; i++) {
        if (gantt[i].fin > tiempoMaximo) {
            tiempoMaximo = gantt[i].fin;
        }
    }

    // Crear matriz de ejecución booleana [n procesos][tiempoMaximo]
    int **ejecucion = (int **)malloc(n * sizeof(int *));
    for (int i = 0; i < n; i++) {
        ejecucion[i] = (int *)calloc(tiempoMaximo, sizeof(int));
    }

    // Marcar los espacios donde se ejecuta cada proceso
    for (int b = 0; b < totalBloquesGantt; b++) {
        int procIdx = -1;
        for (int i = 0; i < n; i++) {
            if (strcmp(procesos[i].id, gantt[b].procesoId) == 0) {
                procIdx = i;
                break;
            }
        }
        if (procIdx != -1) {
            for (int t = gantt[b].inicio; t < gantt[b].fin; t++) {
                ejecucion[procIdx][t] = 1;
            }
        }
    }

    printf("\n--- DIAGRAMA DE GANTT (MATRIZ) ---\n");
    printf("Clock |");
    for (int t = 0; t < tiempoMaximo; t++) {
        printf("  %2d |", t);
    }
    printf("\n-------");
    for (int t = 0; t < tiempoMaximo; t++) {
        printf("-----");
    }
    printf("\n");

    for (int i = 0; i < n; i++) {
        printf(" %-4s |", procesos[i].id);
        for (int t = 0; t < tiempoMaximo; t++) {
            if (t == procesos[i].tiempoLlegada) {
                printf("   X |");
            } else if (ejecucion[i][t] == 1) {
                printf("  ██ |");
            } else {
                printf("     |");
            }
        }
        printf("\n");
    }
    printf("=================================================================\n");

    // Liberar memoria dinámica
    free(procesos);
    free(listaSimulacion);
    free(gantt);
    for (int i = 0; i < n; i++) {
        free(ejecucion[i]);
    }
    free(ejecucion);

    return 0;
}
