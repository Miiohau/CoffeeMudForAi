package com.planet_ink.coffee_mud.Abilities.Spells;

import com.planet_ink.coffee_mud.interfaces.*;
import com.planet_ink.coffee_mud.common.*;
import com.planet_ink.coffee_mud.utils.*;
import java.util.*;

public class Spell_Disintegrate extends Spell
{
	public Spell_Disintegrate()
	{
		super();
		myID=this.getClass().getName().substring(this.getClass().getName().lastIndexOf('.')+1);
		name="Disintegrate";

		quality=Ability.MALICIOUS;

		canBeUninvoked=true;
		isAutoinvoked=false;

		canAffectCode=0;
		canTargetCode=Ability.CAN_MOBS|Ability.CAN_ITEMS;
		
		baseEnvStats().setLevel(25);

		baseEnvStats().setAbility(0);
		uses=Integer.MAX_VALUE;
		recoverEnvStats();
	}

	public Environmental newInstance()
	{
		return new Spell_Disintegrate();
	}
	public int classificationCode()
	{
		return Ability.SPELL|Ability.DOMAIN_EVOCATION;
	}

	public boolean invoke(MOB mob, Vector commands, Environmental givenTarget, boolean auto)
	{
		Environmental target=getAnyTarget(mob,commands,givenTarget,Item.WORN_REQ_ANY);
		if(target==null) return false;

		if(!super.invoke(mob,commands,givenTarget,auto))
			return false;


		boolean success=false;
		affectType=Affect.MSG_CAST_VERBAL_SPELL;
		if(target instanceof Item)
			success=profficiencyCheck(((mob.envStats().level()-target.envStats().level())*25),auto);
		else
		{
			affectType=affectType;
			success=profficiencyCheck(-(target.envStats().level()*3),auto);
		}

		if(auto)affectType=affectType|Affect.ACT_GENERAL;

		if(success)
		{
			FullMsg msg=new FullMsg(mob,target,this,affectType,auto?"":"^S<S-NAME> point(s) at <T-NAMESELF> and utter(s) a treacherous spell!^?");
			if(mob.location().okAffect(msg))
			{
				mob.location().send(mob,msg);
				if(!msg.wasModified())
				{
					Hashtable V=new Hashtable();
					for(int i=0;i<mob.location().numItems();i++)
					{
						Item item=mob.location().fetchItem(i);
						if((item!=null)&&(item instanceof DeadBody))
							V.put(item,item);
					}

					if(target instanceof MOB)
					{
						ExternalPlay.postDamage(mob,(MOB)target,this,(((MOB)target).curState().getHitPoints()*10),Affect.ACT_GENERAL|Affect.TYP_CAST_SPELL,Weapon.TYPE_BURSTING,"^SThe spell <DAMAGE> <T-NAME>!^?");
						if(((MOB)target).amDead())
							mob.location().show(mob,target,Affect.MSG_OK_ACTION,"<T-NAME> disintegrate(s)!");
						else
							return false;
					}
					else
						mob.location().show(mob,target,Affect.MSG_OK_ACTION,"<T-NAME> disintegrate(s)!");

					if(target instanceof Item)
						((Item)target).destroyThis();
					else
					{
						int i=0;
						while(i<mob.location().numItems())
						{
							int s=mob.location().numItems();
							Item item=mob.location().fetchItem(i);
							if((item!=null)&&(item instanceof DeadBody)&&(V.get(item)==null))
								item.destroyThis();
							if(s==mob.location().numItems())
								i++;
						}
					}
					mob.location().recoverRoomStats();
				}

			}

		}
		else
			maliciousFizzle(mob,target,"<S-NAME> point(s) at <T-NAMESELF> and utter(s) a treacherous but fizzled spell!");


		// return whether it worked
		return success;
	}
}