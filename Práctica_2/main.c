#define _DEFAULT_SOURCE
#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <strings.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <time.h>
#include <errno.h>

#define MAX_BUFFER 512

/**
 * Limpia el búfer de entrada estándar (stdin) para prevenir bucles infinitos
 * en caso de lecturas con formato incorrecto o caracteres residuales.
 */
void limpiarBufferStdin(void) {
    int c;
    while ((c = getchar()) != '\n' && c != EOF);
}

/**
 * Lee un entero de forma segura dentro de un rango inclusivo [min, max].
 */
int leerEnteroEnRango(const char *mensaje, int min, int max) {
    int valor;
    while (1) {
        printf("%s", mensaje);
        if (scanf("%d", &valor) == 1) {
            limpiarBufferStdin();
            if (valor >= min && valor <= max) {
                return valor;
            }
            printf("[!] Error: El valor debe estar entre %d y %d. Intente de nuevo.\n", min, max);
        } else {
            printf("[!] Error: Ingrese un numero entero valido.\n");
            limpiarBufferStdin();
        }
    }
}

/**
 * Invierte una cadena de texto carácter por carácter manualmente,
 * sin utilizar ninguna función de librería estándar externa (como strrev).
 */
void invertirCadenaManual(char *cadena) {
    if (cadena == NULL) return;
    
    // Calcular longitud manualmente sin librerías
    int longitud = 0;
    while (cadena[longitud] != '\0') {
        longitud++;
    }

    int inicio = 0;
    int fin = longitud - 1;
    while (inicio < fin) {
        char temp = cadena[inicio];
        cadena[inicio] = cadena[fin];
        cadena[fin] = temp;
        inicio++;
        fin--;
    }
}

// PROBLEMA 1: COMUNICACIÓN UNIDIRECCIONAL Y BIDIRECCIONAL (SISTEMA DE PAGO)
void ejecutarProblema1(void) {
    printf("\n======================================================================\n");
    printf("   PROBLEMA 1: SISTEMA DE PAGO EN LINEA CON VERIFICACION DE ESTADO   \n");
    printf("======================================================================\n");

    printf("\nBienvenido al sistema de pagos de telefonia.\n");
    printf("Opciones de verificacion previa:\n");
    printf("  1. Realizar verificacion de estado del canal\n");
    printf("  2. Omitir verificacion e ir directamente al pago\n");

    int opcVerif = leerEnteroEnRango("Seleccione una opcion (1 o 2): ", 1, 2);

    // FASE 1: Verificación de estado del canal (Opcional - Unidireccional)
    if (opcVerif == 1) {
        printf("\n--- FASE 1: VERIFICACION DE CANAL (UNIDIRECCIONAL) ---\n");
        printf("Ingrese una palabra para probar la conexion del canal: ");
        char palabra[MAX_BUFFER];
        if (fgets(palabra, sizeof(palabra), stdin) == NULL) {
            strcpy(palabra, "PRUEBA_CANAL");
        }
        palabra[strcspn(palabra, "\r\n")] = '\0';

        int pipeVerif[2];
        if (pipe(pipeVerif) == -1) {
            perror("[!] Error al crear la tuberia de verificacion");
            return;
        }

        fflush(stdout);
        pid_t pidVerif = fork();
        if (pidVerif < 0) {
            perror("[!] Error al realizar fork");
            close(pipeVerif[0]);
            close(pipeVerif[1]);
            return;
        }

        if (pidVerif == 0) {
            // --- PROCESO HIJO (Verificador) ---
            close(pipeVerif[1]); // Cierra extremo de escritura no utilizado

            char bufferHijo[MAX_BUFFER];
            memset(bufferHijo, 0, sizeof(bufferHijo));
            ssize_t bytesLeidos = read(pipeVerif[0], bufferHijo, sizeof(bufferHijo) - 1);
            close(pipeVerif[0]);

            if (bytesLeidos > 0) {
                bufferHijo[bytesLeidos] = '\0';
                printf("\n  [HIJO - PID %d]: Recibi la palabra: \"%s\"\n", getpid(), bufferHijo);
                
                // Inversión manual de la cadena carácter por carácter
                invertirCadenaManual(bufferHijo);

                printf("  [HIJO - PID %d]: Inversion manual completada: \"%s\"\n", getpid(), bufferHijo);
                printf("  [HIJO - PID %d]: >>> CONFIRMACION: El canal esta ACTIVO y respondiendo. <<<\n\n", getpid());
            }
            exit(0);
        } else {
            // --- PROCESO PADRE ---
            close(pipeVerif[0]); // Cierra extremo de lectura no utilizado

            printf("  [PADRE - PID %d]: Enviando palabra \"%s\" al hijo por la tuberia...\n", getpid(), palabra);
            write(pipeVerif[1], palabra, strlen(palabra));
            close(pipeVerif[1]); // Cierra escritura para emitir EOF

            waitpid(pidVerif, NULL, 0); // Esperar que el hijo concluya
        }
    } else {
        printf("\n[*] Verificacion omitida por el usuario. Procediendo directamente al pago...\n");
    }

    // FASE 2: Procesamiento del Pago (Bidireccional)
    printf("\n--- FASE 2: PROCESAMIENTO DEL PAGO (BIDIRECCIONAL) ---\n");
    int numeroTarjeta = leerEnteroEnRango("Ingrese su numero de tarjeta (entre 1000 y 9999): ", 1000, 9999);

    int pipePadreHijo[2]; // Padre envía tarjeta al hijo
    int pipeHijoPadre[2]; // Hijo responde estado al padre

    if (pipe(pipePadreHijo) == -1 || pipe(pipeHijoPadre) == -1) {
        perror("[!] Error al crear las tuberias bidireccionales");
        return;
    }

    fflush(stdout);
    pid_t pidPago = fork();
    if (pidPago < 0) {
        perror("[!] Error al crear proceso para el pago");
        return;
    }

    if (pidPago == 0) {
        // --- PROCESO HIJO (Servidor de Validación) ---
        close(pipePadreHijo[1]);
        close(pipeHijoPadre[0]);

        int tarjetaRecibida = 0;
        read(pipePadreHijo[0], &tarjetaRecibida, sizeof(int));
        close(pipePadreHijo[0]);

        printf("  [HIJO - PID %d]: Validando numero de tarjeta recibido: %d\n", getpid(), tarjetaRecibida);

        char respuesta[64];
        if (tarjetaRecibida % 2 == 0) {
            strcpy(respuesta, "PAGO_APROBADO");
        } else {
            strcpy(respuesta, "PAGO_RECHAZADO");
        }

        printf("  [HIJO - PID %d]: Evaluacion: %s. Enviando veredicto al padre...\n", getpid(), respuesta);
        write(pipeHijoPadre[1], respuesta, strlen(respuesta) + 1);
        close(pipeHijoPadre[1]);

        exit(0);
    } else {
        // --- PROCESO PADRE (Cliente / Terminal) ---
        close(pipePadreHijo[0]);
        close(pipeHijoPadre[1]);

        printf("  [PADRE - PID %d]: Enviando tarjeta %d al servidor de pagos (hijo)...\n", getpid(), numeroTarjeta);
        write(pipePadreHijo[1], &numeroTarjeta, sizeof(int));
        close(pipePadreHijo[1]);

        char respuestaFinal[64];
        memset(respuestaFinal, 0, sizeof(respuestaFinal));
        read(pipeHijoPadre[0], respuestaFinal, sizeof(respuestaFinal) - 1);
        close(pipeHijoPadre[0]);

        waitpid(pidPago, NULL, 0);

        printf("\n======================================================================\n");
        printf("  RESULTADO FINAL DEL SISTEMA (PADRE): %s\n", respuestaFinal);
        if (strcmp(respuestaFinal, "PAGO_APROBADO") == 0) {
            printf("  Estado: Transaccion completada con exito.\n");
        } else {
            printf("  Estado: La transaccion fue declinada por fondos insuficientes o rechazo.\n");
        }
        printf("======================================================================\n");
    }
}

// PROBLEMA 2: PRODUCTOR-CONSUMIDOR (BODEGA DE CAMISAS)
void ejecutarProblema2(void) {
    printf("\n======================================================================\n");
    printf("   PROBLEMA 2: BODEGA DE CAMISAS (PRODUCTOR - CONSUMIDORES EN PARALELO)\n");
    printf("======================================================================\n");

    const int TOTAL_PEDIDOS = 20;
    int pedidos[20];

    printf("\nOpciones de ingreso de los 20 pedidos:\n");
    printf("  1. Ingresar manualmente los 20 pedidos (valores entre 1 y 100)\n");
    printf("  2. Generar aleatoriamente los 20 pedidos (para evaluacion rapida)\n");
    int modalidad = leerEnteroEnRango("Seleccione modalidad (1 o 2): ", 1, 2);

    if (modalidad == 1) {
        printf("\n--- Registro de pedidos del dia (1 a 100 camisas por pedido) ---\n");
        for (int i = 0; i < TOTAL_PEDIDOS; i++) {
            char prompt[64];
            snprintf(prompt, sizeof(prompt), "Pedido #%02d - Cantidad de camisas (1-100): ", i + 1);
            pedidos[i] = leerEnteroEnRango(prompt, 1, 100);
        }
    } else {
        srand((unsigned int)time(NULL));
        printf("\n--- Generando 20 pedidos aleatorios [1 - 100] ---\n");
        for (int i = 0; i < TOTAL_PEDIDOS; i++) {
            pedidos[i] = (rand() % 100) + 1;
            printf("  Pedido #%02d: %2d camisas%s", i + 1, pedidos[i], ((i + 1) % 4 == 0) ? "\n" : "  |  ");
        }
        printf("\n");
    }

    printf("\n[COORDINADOR]: Registro de los 20 pedidos finalizado.\n");
    printf("[COORDINADOR]: Apertura de la banda transportadora (tuberia) y despliegue de estaciones...\n");

    int bandaTransportadora[2];
    if (pipe(bandaTransportadora) == -1) {
        perror("[!] Error al crear la banda transportadora");
        return;
    }

    // Creación de Estación de Empaque 1 (Hijo 1)
    fflush(stdout);
    pid_t pidEstacion1 = fork();
    if (pidEstacion1 < 0) {
        perror("[!] Error al crear Estacion 1");
        return;
    }

    if (pidEstacion1 == 0) {
        // --- ESTACIÓN DE EMPAQUE 1 ---
        close(bandaTransportadora[1]);

        int pedidosProcesados = 0;
        int camisasDespachadas = 0;
        int pedidoActual = 0;

        srand((unsigned int)time(NULL) ^ (getpid() << 16));

        while (read(bandaTransportadora[0], &pedidoActual, sizeof(int)) > 0) {
            pedidosProcesados++;
            camisasDespachadas += pedidoActual;
            printf("  [ESTACION 1 - PID %d]: Despachando pedido de %2d camisas. (Acumulado: %d pedidos / %d camisas)\n",
                   getpid(), pedidoActual, pedidosProcesados, camisasDespachadas);
            // Simulación de latencia de empaque para asegurar concurrencia real
            usleep(15000 + (rand() % 25000));
        }

        close(bandaTransportadora[0]);

        printf("\n=======================================================\n");
        printf("   REPORTE FINAL: ESTACION DE EMPAQUE 1 (PID: %d)     \n", getpid());
        printf("=======================================================\n");
        printf("   Total de pedidos procesados : %d pedidos\n", pedidosProcesados);
        printf("   Total de camisas despachadas: %d unidades\n", camisasDespachadas);
        printf("=======================================================\n");

        exit(0);
    }

    // Creación de Estación de Empaque 2 (Hijo 2)
    fflush(stdout);
    pid_t pidEstacion2 = fork();
    if (pidEstacion2 < 0) {
        perror("[!] Error al crear Estacion 2");
        return;
    }

    if (pidEstacion2 == 0) {
        // --- ESTACIÓN DE EMPAQUE 2 ---
        close(bandaTransportadora[1]);

        int pedidosProcesados = 0;
        int camisasDespachadas = 0;
        int pedidoActual = 0;

        srand((unsigned int)time(NULL) ^ (getpid() << 16));

        while (read(bandaTransportadora[0], &pedidoActual, sizeof(int)) > 0) {
            pedidosProcesados++;
            camisasDespachadas += pedidoActual;
            printf("  [ESTACION 2 - PID %d]: Despachando pedido de %2d camisas. (Acumulado: %d pedidos / %d camisas)\n",
                   getpid(), pedidoActual, pedidosProcesados, camisasDespachadas);
            // Simulación de latencia de empaque para asegurar concurrencia real
            usleep(15000 + (rand() % 25000));
        }

        close(bandaTransportadora[0]);

        printf("\n=======================================================\n");
        printf("   REPORTE FINAL: ESTACION DE EMPAQUE 2 (PID: %d)     \n", getpid());
        printf("=======================================================\n");
        printf("   Total de pedidos procesados : %d pedidos\n", pedidosProcesados);
        printf("   Total de camisas despachadas: %d unidades\n", camisasDespachadas);
        printf("=======================================================\n");

        exit(0);
    }

    // --- PROCESO COORDINADOR (PADRE) ---
    close(bandaTransportadora[0]); // El padre no lee de la banda

    printf("[COORDINADOR]: Enviando inmediatamente los 20 pedidos por la banda transportadora...\n\n");
    for (int i = 0; i < TOTAL_PEDIDOS; i++) {
        write(bandaTransportadora[1], &pedidos[i], sizeof(int));
    }

    // Cierre del extremo de escritura del padre: envía señal EOF a las estaciones
    close(bandaTransportadora[1]);

    // Esperar a que ambas estaciones terminen sus reportes
    waitpid(pidEstacion1, NULL, 0);
    waitpid(pidEstacion2, NULL, 0);

    printf("\n[COORDINADOR]: Ambas estaciones han terminado. La banda transportadora quedo vacia.\n");
}

int main(void) {
    int opcion = 0;

    while (1) {
        printf("\n================================================================================\n");
        printf("                     PRÁCTICA NO. 2 - TUBERIAS     \n");
        printf("================================================================================\n");
        printf("  1. Problema 1: Sistema de Pago en Linea (Pipes Unidireccional y Bidireccional)\n");
        printf("  2. Problema 2: Bodega de Camisas (Productor-Consumidor en Paralelo)\n");
        printf("  3. Salir\n");
        printf("================================================================================\n");

        opcion = leerEnteroEnRango("Seleccione una opcion (1-3): ", 1, 3);

        switch (opcion) {
            case 1:
                ejecutarProblema1();
                break;
            case 2:
                ejecutarProblema2();
                break;
            case 3:
                printf("\nSaliendo del programa...\n");
                exit(0);
        }

        printf("\nPresione ENTER para volver al menu principal...");
        limpiarBufferStdin();
    }

    return 0;
}
