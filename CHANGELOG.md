# Changelog

## 0.1.0 (Beta)

First public version, released as a beta. I tested it thoroughly with automated tests, but I do not play with Iron's Spells 'n Spellbooks myself, so your reports are the real test. Please report bugs on the [issue tracker](https://github.com/Drehverschluss/RunelessRPG-Mana-Bridge/issues) and add the output of `/runelessrpg_bridge status`.

- **One shared mana pool for RunelessRPG and Iron's Spells 'n Spellbooks**: Iron's spells, its Mana potion, Gluttony and the `/mana` command use the same mana as Spell Engine spells (1 Iron's mana = 0.2 RunelessRPG mana by default, set automatically from the base values of both mods).
- **The pack chooses which mod leads the pool** with `manaSystem`: `"runelessrpg"` (default) or `"irons_spellbooks"`. The leading mod keeps its own base mana, regeneration and respawn rule. The switch also works at runtime and keeps every player's mana and debt.
- **Gear of the other mod counts too**: Iron's armor, rings, spell books and upgrade orbs raise RunelessRPG's max mana and regeneration, and RunelessRPG's enchantments, affixes and gear raise Iron's mana while Iron's leads. Flat values are converted, percent values stay percent, so the same gear gives the same share of the pool in both modes.
- **Iron's spell costs follow `runelessrpg:mana_cost`** (with its lower limit and Spell Infinity) and the debt rules of Spell Engine spells (DEBT modes, Borrowed Power). Debt works in both modes.
- **Every player chooses the mana bar** (RunelessRPG, Iron's or both) and the unit of every displayed amount. Iron's spell tooltips, the spell wheel, the Inscription Table and max mana values on gear are converted. The RunelessRPG bar shows while you are in mana debt, and Iron's bar also shows while you hold a Spell Engine spell weapon.
- **Spell Mana Leech works on direct hits of Iron's spells**; two entity tags change which hits count.
- **Other mana bridges** (Ars 'n Spells, Ars Mana Bridge, Mana Transform, Unified Mana) are detected: the Iron's adapter turns off with a warning in the log and a chat message for operators, but only while the other bridge's mana function is on. A self test at startup turns the adapter off if Iron's changes, instead of breaking the game.
- **New command `/runelessrpg_bridge status`** for bug reports: versions, leading pool, adapters, other bridges, the important config values and your own mana, with a click that copies everything as English text. Every player can use it for themselves; operators and the console can name a player.
- The config keeps the Iron's section while Iron's is not installed. Clients without this mod can still join a server with it, and clients with it can join servers without it.
- Ars Nouveau is not connected yet; with Ars installed the bridge does nothing for it.
