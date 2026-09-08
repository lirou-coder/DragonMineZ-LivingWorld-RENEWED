scoreboard players add #clock lw_clock 1
execute as @a unless score @s lw_version matches 616 run function dmzlivingworld:player/init
execute as @a at @s unless predicate dmzlivingworld:is_overworld run function dmzlivingworld:hunting/outside_overworld
execute if score #clock lw_clock matches 200.. run function dmzlivingworld:pulse_10s
execute if score #clock lw_clock matches 200.. run scoreboard players set #clock lw_clock 0
