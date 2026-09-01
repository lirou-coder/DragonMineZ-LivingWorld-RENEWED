scoreboard players set @s lw_scout_state 1
scoreboard players set @s lw_scout_timer 3
scoreboard players set @s lw_huntcd 18
playsound minecraft:block.amethyst_block.chime player @s ~ ~ ~ 0.5 0.6
tellraw @s [{"text":"[Living World] ","color":"gold"},{"text":"You get the feeling that someone is watching your movements...","color":"gray","italic":true}]
execute positioned ^40 ^ ^55 positioned over world_surface run summon dragonminez:saga_friezasoldier01 ~ ~1 ~ {Tags:["lw_frieza_scout","lw_hunter"]}
