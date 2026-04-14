from ursina import *
from abc import ABC, abstractmethod

app = Ursina()
window.title = 'Battle Arena'


bg_music = Audio('skrillex.mp3', loop=True, autoplay=False, volume=0.5)

FIELD_BOUNDARY = 24



class IBehavior(ABC):
    @abstractmethod
    def update(self, unit, target): pass


class MeleeBehavior(IBehavior):
    def update(self, unit, target):
        if not target: return
        dist = distance(unit.position, target.position)
        if dist > 2.0:
            unit.look_at(target)
            new_pos = unit.position + unit.forward * time.dt * 5
            new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            unit.position = new_pos
        else:
            target.health -= time.dt * 40


class RangedBehavior(IBehavior):
    def update(self, unit, target):
        if not target: return
        dist = distance(unit.position, target.position)
        if dist > 10:
            unit.look_at(target)
            new_pos = unit.position + unit.forward * time.dt * 4
            new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            unit.position = new_pos
        elif dist < 6:
            unit.look_at(target)
            new_pos = unit.position - unit.forward * time.dt * 3
            new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            unit.position = new_pos

        if dist < 15:
            damage = time.dt * 20
            if target.unit_type == 'assassin':
                damage *= 0.3
            target.health -= damage


class TankBehavior(IBehavior):
    def update(self, unit, target):
        if not target: return
        dist = distance(unit.position, target.position)
        if dist > 3.0:
            unit.look_at(target)
            new_pos = unit.position + unit.forward * time.dt * 2
            new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
            unit.position = new_pos
        else:
            target.health -= time.dt * 25


class AssassinBehavior(IBehavior):
    def update(self, unit, target):
        if not target: return
        dist = distance(unit.position, target.position)

        if 7.0 < dist < 15.0:
            unit.look_at(target)
            speed = 8 * 2.5
            new_pos = unit.position + unit.forward * time.dt * speed
        elif dist > 2.0:
            unit.look_at(target)
            new_pos = unit.position + unit.forward * time.dt * 8
        else:
            target.health -= time.dt * 60
            new_pos = unit.position

        new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
        new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
        unit.position = new_pos


class HealerBehavior(IBehavior):
    def update(self, unit, target):
        allies = bss.team1 if unit.team == 1 else bss.team2
        heal_target = None
        min_health = 100

        for ally in allies:
            if ally and hasattr(ally, 'health') and ally.health < 100 and ally.health > 0:
                d = distance(unit.position, ally.position)
                if d < 10 and ally.health < min_health:
                    min_health = ally.health
                    heal_target = ally

        if heal_target:
            dist = distance(unit.position, heal_target.position)
            if dist > 5:
                unit.look_at(heal_target)
                new_pos = unit.position + unit.forward * time.dt * 4
                new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
                new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
                unit.position = new_pos
            else:
                heal_target.health += time.dt * 30
                if heal_target.health > 100:
                    heal_target.health = 100
        elif target:
            dist = distance(unit.position, target.position)
            if dist > 10:
                unit.look_at(target)
                new_pos = unit.position + unit.forward * time.dt * 4
                new_pos.x = clamp(new_pos.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
                new_pos.z = clamp(new_pos.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
                unit.position = new_pos
            if dist < 15:
                target.health -= time.dt * 15


class Unit(Entity):
    UNIT_STATS = {
        'melee': {'hp': 100, 'cost': 10, 'model': 'cube', 'scale': (1.5, 2.5, 1.5)},
        'ranged': {'hp': 80, 'cost': 15, 'model': 'sphere', 'scale': (1.3, 2.0, 1.3)},
        'tank': {'hp': 200, 'cost': 25, 'model': 'cube', 'scale': (2.0, 3.0, 2.0)},
        'assassin': {'hp': 60, 'cost': 20, 'model': 'sphere', 'scale': (1.0, 2.0, 1.0)},
        'healer': {'hp': 90, 'cost': 20, 'model': 'sphere', 'scale': (1.4, 2.3, 1.4)},
    }

    BEHAVIOR_MAP = {
        'melee': MeleeBehavior,
        'ranged': RangedBehavior,
        'tank': TankBehavior,
        'assassin': AssassinBehavior,
        'healer': HealerBehavior,
    }

    DESCRIPTIONS = {
        'melee': 'Воин - сбалансированный боец',
        'ranged': 'Лучник - атакует с дистанции',
        'tank': 'Танк - много здоровья',
        'assassin': 'Убийца - быстрый, прыгает к цели',
        'healer': 'Целитель - лечит союзников',
    }

    def __init__(self, pos, team, behavior_type, col=None):
        stats = self.UNIT_STATS.get(behavior_type, self.UNIT_STATS['melee'])

        if col is None:
            col = color.blue if team == 1 else color.red

        type_colors = {
            'melee': color.blue if team == 1 else color.red,
            'ranged': color.cyan if team == 1 else color.orange,
            'tank': color.rgb(0, 0, 150) if team == 1 else color.rgb(150, 0, 0),
            'assassin': color.rgb(150, 0, 150) if team == 1 else color.yellow,
            'healer': color.lime if team == 1 else color.green,
        }
        final_color = type_colors.get(behavior_type, col)

        pos = self._find_valid_position(pos, team)

        super().__init__(
            model=stats['model'],
            color=final_color,
            position=pos,
            scale=stats['scale'],
            collider='box',
            texture='white_cube'
        )

        self.team = team
        self.unit_type = behavior_type
        self.health = stats['hp']
        self.max_health = stats['hp']
        self.behavior = self.BEHAVIOR_MAP.get(behavior_type, MeleeBehavior)()

    def _find_valid_position(self, pos, team):
        all_units = bss.team1 + bss.team2
        min_distance = 2.5

        for attempt in range(10):
            is_valid = True
            for unit in all_units:
                if unit and hasattr(unit, 'position') and unit.health > 0:
                    dist = distance(pos, unit.position)
                    if dist < min_distance:
                        is_valid = False
                        offset_x = (attempt + 1) * 0.5
                        offset_z = (attempt + 1) * 0.5
                        pos = Vec3(
                            clamp(pos.x + offset_x, -FIELD_BOUNDARY, FIELD_BOUNDARY),
                            pos.y,
                            clamp(pos.z + offset_z, -FIELD_BOUNDARY, FIELD_BOUNDARY)
                        )
                        break

            if is_valid:
                break
        return pos

    def update(self):
        if not bss.battle_started: return
        if self.health <= 0:
            self.color = color.gray
            invoke(destroy, self, delay=2)
            return

        all_units = bss.team1 + bss.team2
        for unit in all_units:
            if unit and unit != self and unit.health > 0:
                dist = distance(self.position, unit.position)
                if dist < 2.0 and dist > 0:
                    push_direction = (self.position - unit.position).normalized()
                    push_strength = 2.0 * time.dt
                    self.position += push_direction * push_strength

        enemies = bss.team2 if self.team == 1 else bss.team1
        target = None
        min_dist = 999

        for e in enemies:
            if e and hasattr(e, 'health') and e.health > 0:
                d = distance(self.position, e.position)
                if d < min_dist:
                    min_dist = d
                    target = e

        if target:
            self.behavior.update(self, target)

        self.position.x = clamp(self.position.x, -FIELD_BOUNDARY, FIELD_BOUNDARY)
        self.position.z = clamp(self.position.z, -FIELD_BOUNDARY, FIELD_BOUNDARY)


class BattleSystem:
    def __init__(self):
        self.team1 = []
        self.team2 = []
        self.p1_money = 100
        self.p2_money = 100

        self.current_player = 1
        self.selected_type = 'melee'
        self.battle_started = False

        self.ui = Text(text='', position=(-0.85, 0.45), scale=1.5, color=color.blue)
        self.money_text = Text(text='', position=(-0.85, 0.40), scale=1.2, color=color.gold)

        self.ghost = Entity(model='cube', color=color.rgba(0, 255, 0, 150),
                            scale=(1.5, 2.5, 1.5), alpha=0.5)

        self.desc_text = Text(
            text='1:Воин  2:Лучник  3:Танк  4:Убийца  5:Целитель',
            position=(-0.85, 0.35), scale=1.1, color=color.gray
        )


bss = BattleSystem()


def update():
    if bss.battle_started: return

    money = bss.p1_money if bss.current_player == 1 else bss.p2_money
    bss.money_text.text = f'Золото: {money}'

    if mouse.hovered_entity == ground:
        gp = mouse.world_point
        gx, gz = round(gp.x), round(gp.z)
        bss.ghost.position = Vec3(gx, 1.25, gz)
        bss.ghost.enabled = True

        stats = Unit.UNIT_STATS[bss.selected_type]
        bss.ghost.scale = stats['scale']

        zone_ok = (bss.current_player == 1 and gx < -1) or (bss.current_player == 2 and gx > 1)
        money_ok = money >= stats['cost']

        if zone_ok and money_ok:
            bss.ghost.color = color.rgba(0, 255, 0, 150)
        else:
            bss.ghost.color = color.rgba(255, 0, 0, 150)
    else:
        bss.ghost.enabled = False

    desc = Unit.DESCRIPTIONS.get(bss.selected_type, '')
    cost = Unit.UNIT_STATS[bss.selected_type]['cost']
    bss.ui.text = f'P{bss.current_player}: {desc} ({cost} золота)'


def input(key):
    if bss.battle_started: return

    type_map = {'1': 'melee', '2': 'ranged', '3': 'tank', '4': 'assassin', '5': 'healer'}
    if key in type_map:
        bss.selected_type = type_map[key]

    if key == 'left mouse down' and bss.ghost.enabled:
        if bss.ghost.color == color.rgba(0, 255, 0, 150):
            stats = Unit.UNIT_STATS[bss.selected_type]
            u = Unit(bss.ghost.position, bss.current_player, bss.selected_type)

            if bss.current_player == 1:
                bss.team1.append(u)
                bss.p1_money -= stats['cost']
            else:
                bss.team2.append(u)
                bss.p2_money -= stats['cost']

    if key == 'enter':
        if bss.current_player == 1:
            bss.current_player = 2
            bss.ui.color = color.red
        else:
            bss.battle_started = True

            bg_music.play()

            bss.ghost.enabled = False
            bss.ui.text = 'БОЙ НАЧАЛСЯ!'
            bss.ui.color = color.yellow
            bss.desc_text.enabled = False
            bss.money_text.enabled = False


ground = Entity(model='cube', scale=(50, 0.5, 50), color=color.white,
                collider='box', y=-0.25, texture='white_cube')
line = Entity(model='cube', scale=(0.5, 0.1, 50), color=color.black, z=0, y=0.1)

boundary_north = Entity(model='cube', scale=(50, 0.2, 0.5), color=color.gray, z=-25, y=0)
boundary_south = Entity(model='cube', scale=(50, 0.2, 0.5), color=color.gray, z=25, y=0)
boundary_west = Entity(model='cube', scale=(0.5, 0.2, 50), color=color.gray, x=-25, y=0)
boundary_east = Entity(model='cube', scale=(0.5, 0.2, 50), color=color.gray, x=25, y=0)

Sky(color=color.cyan)
AmbientLight(color=color.white)
DirectionalLight(y=20, rotation=(45, 45, 45), shadows=True)

camera.position = (0, 25, -200)
camera.look_at(ground)
EditorCamera()

app.run()
