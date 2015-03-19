package com.treeapps.audiorecorder;

import android.util.Log;

import java.util.ArrayList;

/**
 * Created by HeinrichWork on 12/02/2015.
 */
public class StateMachine <S extends Enum<S>, E extends Enum<E>> {

    private final String TAG = "StateMachine";


    public interface StateTransition <S extends Enum<S>, E extends Enum<E>> {
        void onState(S newState, S prevState, E triggerEvent );
    }

    public interface EventTransition <S extends Enum<S>, E extends Enum<E>> {
        S nextState(E triggerEvent, S prevState);
    }

    private Enum<S> state;


    private ArrayList<StateDef> stateDefs = new ArrayList<StateDef>() ;
    private ArrayList<EventDef> eventDefs = new ArrayList<EventDef>() ;

    public void setInitialState(S initialState) {
        state = initialState;
        for (StateDef stateDef: stateDefs) {
            if (stateDef.state.equals(state)) {
                stateDef.stateTransition.onState(state, null, null);
            }
        }
    }

    public Enum<S> getState() {
        return state;
    }

    public void triggerEvent(E event) {
        Enum<S> prevState = state;
        boolean boolIsEventFound = false;
        for (EventDef eventDef: eventDefs) {
            if (eventDef.event.equals(event)) {
                state = eventDef.eventTransition.nextState(event,state);
                if (state == null) return; // To allow triggering another event within an event
                boolIsEventFound =  true;
                break;
            }
        }
        if (!boolIsEventFound) {
            Exception ex = new Exception("Event " + event.toString() + " has not been defined");
            Log.e(TAG, "State Machine Error", ex);
        }
        for (StateDef stateDef: stateDefs) {
            if (stateDef.state.equals(state)) {
                stateDef.stateTransition.onState(state, prevState, event);
                break;
            }
        }
    }

    public void onTriggeringEvent(E event, EventTransition eventTransition) {
        eventDefs.add(new EventDef(event,eventTransition));
    }

    public void onEnteringState(S enterState, StateTransition stateTransition ) {
        stateDefs.add(new StateDef(enterState,stateTransition));
    }

    private class StateDef {
        public S state;
        public StateTransition stateTransition;

        private StateDef(S state, StateTransition stateTransition) {
            this.state = state;
            this.stateTransition = stateTransition;
        }
    }

    private class EventDef {
        public E event;
        public  EventTransition eventTransition;


        private EventDef(E event, EventTransition eventTransition) {
            this.event = event;
            this.eventTransition = eventTransition;
        }
    }

}
