package com.distributed.chatapp;

import java.io.Serializable;
import java.util.ArrayList;

public class SharedState implements Serializable {

  public String liderName = null;
  public String masterName = null;
  public GameStatus status = null;
  public ArrayList<String> readyPlayers = new ArrayList<>();
  public String a  = null;
  public String b = null;
  public String lastView = null;

}
