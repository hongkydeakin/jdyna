package com.dawidweiss.dyna.players;

import com.dawidweiss.dyna.*;

/**
 * Factory of human players bound to the keyboard controller. Not for real use, I guess.
 */
public final class HumanPlayerFactory implements IPlayerFactory
{
    private IPlayerController controller;

    public HumanPlayerFactory()
    {
        this(Globals.getDefaultKeyboardController(0));
    }

    public HumanPlayerFactory(IPlayerController controller)
    {
        this.controller = controller;
    }

    @Override
    public IPlayerController getController(String playerName)
    {
        return controller;
    }

    @Override
    public String getDefaultPlayerName()
    {
        return "Human";
    }

    @Override
    public String getVendorName()
    {
        return "Dawid Weiss";
    }
}