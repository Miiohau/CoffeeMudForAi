package com.planet_ink.coffee_mud.Abilities.Thief;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB.Attrib;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.*;

/*
   Copyright 2024-2024 Bo Zimmerman

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
public class Thief_Kidnapping extends ThiefSkill
{
	@Override
	public String ID()
	{
		return "Thief_Kidnapping";
	}

	private final static String localizedName = CMLib.lang().L("Kidnapping");

	@Override
	public String name()
	{
		return localizedName;
	}

	@Override
	protected int canAffectCode()
	{
		return CAN_MOBS;
	}

	@Override
	protected int canTargetCode()
	{
		return CAN_MOBS;
	}

	@Override
	public int abstractQuality()
	{
		return Ability.QUALITY_MALICIOUS;
	}

	protected boolean reAssist = false; // false means assist is ON
	protected Reference<MOB> parentM = null;
	protected long parentTimeout = System.currentTimeMillis()-1;
	protected volatile int aloneTicker = -1;
	protected Map<MOB,Long> failures = new Hashtable<MOB,Long>();

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_THIEF_SKILL|Ability.DOMAIN_CRIMINAL;
	}

	private static final String[] triggerStrings =I(new String[] {"KIDNAP","KIDNAPPING"});
	@Override
	public String[] triggerStrings()
	{
		return triggerStrings;
	}

	@Override
	public long flags()
	{
		return Ability.FLAG_CHARMING;
	}

	@Override
	public int usageType()
	{
		return USAGE_MOVEMENT;
	}

	@Override
	public void unInvoke()
	{
		if(affected instanceof MOB)
			((MOB)affected).setAttribute(Attrib.AUTOASSIST, reAssist);
		super.unInvoke();
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		//super.setMiscText(newMiscText);
		reAssist = CMParms.getParmBool(newMiscText, "NOASSIST", false); // false is ASSIST ON, which is confusing here, i get it
		final String invoker = CMParms.getParmStr(newMiscText, "INVOKER", "");
		if(invoker.length()>0)
		{
			if((invoker()==null)||(!invoker().name().equalsIgnoreCase(invoker)))
			{
				final MOB invokerM = CMLib.players().getLoadPlayer(invoker);
				if(invokerM != null)
					setInvoker(invokerM);
			}
		}
	}

	@Override
	public String text()
	{
		if(invoker() != null)
			return "NOASSIST="+reAssist+" INVOKER=\""+invoker().Name()+"\"";
		else
			return "NOASSIST="+reAssist;
	}

	protected boolean isKidnappable(final MOB kidnapperM, final MOB M)
	{
		if((M.charStats().ageCategory()>=Race.AGE_YOUNGADULT)
		&&(!CMLib.flags().isAgingChild(M))
		&&(!CMLib.flags().isAnimalIntelligence(M))
		&&(!CMStrings.containsWord(M.name().toLowerCase(), "child"))
		&&(!CMStrings.containsWord(M.name().toLowerCase(), "kid")))
			return false;
		if(M.isPlayer()||(!M.isMonster()))
			return kidnapperM.mayIFight(M);
		if(M.amFollowing()!=null)
			return kidnapperM.mayIFight(M.amUltimatelyFollowing());
		return true;
	}

	protected MOB getReturnParent()
	{
		final Physical P = affected;
		if((parentM == null)
		||(System.currentTimeMillis()>parentTimeout))
		{
			if(!(P instanceof Tattooable))
				return null;
			final Tattooable TP = (Tattooable)P;
			for(final Enumeration<Tattoo> t = TP.tattoos();t.hasMoreElements();)
			{
				final Tattoo T = t.nextElement();
				if(T.ID().startsWith("PARENT:"))
				{
					final String parentName = T.ID().substring(7);
					final MOB M = CMLib.players().getPlayer(parentName);
					if((M!=null)&&(CMLib.flags().isInTheGame(M, true)))
					{
						parentM = new WeakReference<MOB>(M);
						parentTimeout = Long.MAX_VALUE; // don't timeout, this is perfect
						return M;
					}
					if(M != null)
						parentM = new WeakReference<MOB>(M);
				}
			}
			if(parentM == null)
				parentM = new WeakReference<MOB>(null);
			parentTimeout = System.currentTimeMillis() + TimeManager.MILI_HOUR;
		}
		return parentM.get();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(affected instanceof MOB)
		{
			final MOB M = (MOB)affected;
			if((!M.isAttributeSet(Attrib.AUTOASSIST))
			&&(!CMLib.flags().isAgingChild(affected)))
				M.setAttribute(Attrib.AUTOASSIST, true); // true means its assist is turned OFF
			if(invoker() == null)
				return true;
			if(M.isInCombat())
			{
				final MOB vicM = M.getVictim();
				if((vicM != null)
				&& (vicM==invoker().getVictim())
				&&(vicM.getVictim()!=M))
					M.makePeace(false);
			}
			final Room R = M.location();
			if((R!=null)
			&&((M.amFollowing()==null)||(!R.isInhabitant(invoker())))
			&&(R.numInhabitants()==1))
			{
				if(this.aloneTicker < 0)
					this.aloneTicker = 5; // 20 seconds to get it right
				else
				if(--this.aloneTicker<=0)
				{
					final MOB parentM = this.getReturnParent();
					if(parentM == null)
						this.aloneTicker = 40;
					else
					{
						// attempt escape!
						int dir=-1;
						for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
						{
							final Room escapeR = R.getRoomInDir(d);
							if(escapeR != null)
							{
								final Exit E = R.getExitInDir(d);
								if((E!=null)
								&&(E.isOpen()||(!E.isLocked()))
								&&(CMLib.flags().canBeSeenBy(E, M)))
								{
									dir = d;
									break;
								}
							}
						}
						if(dir >= 0)
						{
							final Room escapeR = R.getRoomInDir(dir);
							final Exit E = R.getExitInDir(dir);
							if(!E.isOpen())
								CMLib.commands().postOpen(M, E, false);
							if(E.isOpen())
								CMLib.tracking().walk(M, dir, true, false);
							if(M.location() == escapeR)
							{
								this.unInvoke();
								M.delEffect(this);
								M.setFollowing(null);
								CMLib.tracking().autoTrack(M, parentM.location());
								return false;
							}
						}
					}
				}
			}
		}
		return true;
	}

	@Override
	public int castingQuality(final MOB mob, final Physical target)
	{
		if((mob!=null)&&(target!=null))
		{
			if(!(target instanceof MOB))
				return Ability.QUALITY_INDIFFERENT;
			if(mob.isInCombat())
				return Ability.QUALITY_INDIFFERENT;
			if(!isKidnappable(mob, (MOB)target))
				return Ability.QUALITY_INDIFFERENT;
			if(failures.containsKey(target) && (System.currentTimeMillis()<failures.get(target).longValue()))
				return Ability.QUALITY_INDIFFERENT;
		}
		return super.castingQuality(mob,target);
	}

	@Override
	public boolean invoke(final MOB mob, final List<String> commands, final Physical givenTarget, final boolean auto, final int asLevel)
	{
		if((mob.isInCombat())&&(!auto))
		{
			mob.tell(L("Not while you are fighting!"));
			return false;
		}
		final MOB target=getTarget(mob,commands,givenTarget);
		if(target==null)
			return false;

		if(failures.containsKey(target) && (System.currentTimeMillis()<failures.get(target).longValue()))
		{
			mob.tell(L("You can't attempt to kidnap @x1 again so soon.",target.name(mob)));
			return false;
		}
		failures.remove(target);

		if(target.amFollowing()==mob)
		{
			mob.tell(L("@x1 is already your follower.",target.name(mob)));
			return false;
		}

		if(!isKidnappable(mob, target))
		{
			mob.tell(L("@x1 doesn't seem like a viable target.",target.name(mob)));
			return false;
		}

		if(!super.invoke(mob,commands,givenTarget,auto,asLevel))
			return false;

		final boolean success=proficiencyCheck(mob,0,auto);
		if(success)
		{
			final String str=auto?L("<T-NAME> become(s) kidnapped by <S-NAME>."):L("<S-NAME> kidnap(s) <T-NAME>!");
			final CMMsg msg=CMClass.getMsg(mob,target,this,(auto?CMMsg.MASK_ALWAYS:0)|CMMsg.MSG_THIEF_ACT|CMMsg.MASK_SOUND|CMMsg.MASK_MALICIOUS,str);
			if(target.location().okMessage(mob,msg))
			{
				target.location().send(mob,msg);
				if(msg.value()<=0)
				{
					if(target.amFollowing() != null)
						CMLib.commands().postFollow(target, null, true);
					if(target.amFollowing() != null)
					{
						failures.put(target, Long.valueOf(System.currentTimeMillis()+TimeManager.MILI_HOUR));
						return maliciousFizzle(mob,target,L("<S-NAME> attempt(s) to kidnap <T-NAME> and fail(s)."));
					}
					else
						CMLib.commands().postFollow(target, mob, false);
					final boolean autoAssist = target.isAttributeSet(Attrib.AUTOASSIST);
					final Thief_Kidnapping kA = (Thief_Kidnapping)beneficialAffect(mob, target, asLevel, 0);
					if(kA != null)
					{
						kA.reAssist = autoAssist;
						kA.invoker = mob;
						kA.makeNonUninvokable();
					}
				}
			}
		}
		else
		{
			failures.put(target, Long.valueOf(System.currentTimeMillis()+TimeManager.MILI_HOUR));
			return maliciousFizzle(mob,target,L("<S-NAME> attempt(s) to kidnap <T-NAME> and fail(s)."));
		}

		// return whether it worked
		return success;
	}
}
