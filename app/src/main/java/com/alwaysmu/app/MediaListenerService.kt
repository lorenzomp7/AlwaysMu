package com.alwaysmu.app

import android.service.notification.NotificationListenerService

/**
 * Non legge né modifica le notifiche: esiste solo perché Android concede l'elenco
 * delle sessioni multimediali attive (e i comandi play/pausa) alle app che hanno
 * l'"Accesso alle notifiche".
 */
class MediaListenerService : NotificationListenerService()
