package com.distributed.chatapp;

import java.io.Serializable;

public enum GameStatus implements Serializable {
  CHOOSING_MASTER,
  THINKING,
  PLAYING,
  ROUNDSTARTED
}
