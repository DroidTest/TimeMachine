#!/usr/bin/python
import heapq
import math
from utils import K_Path_Traveller
from collections import Counter


class State:
    MAX_TRANSITIONS_TO_EXISTING_STATE = 5
    MAX_FITNESS = 200.0

    def __init__(self, uid):
        self.uid = uid  # Unique ID representing this state
        self.out_nodes = {}  # Direct, reachable successor states
        self.hit_count = 1  # Number of times this state was naturally reached
        self.transitions_triggered = 0  # Number of times this state was fuzzed (1 time fuzz = generate a transition to another state)
        self.restore_count = 0  # number of time this state was chosen to be restored in VirtualBox
        self.number_events_to_transition = []  # number of events which trigger a transition to another state
        self.controllable_widgets = 0  # Number of controllable widgets
        self.fitness_score = 100.0  # Used for selection (start at 100)
        self.num_transitions_to_existing_state = 0  # How many times the state was fuzzed and transitioned to an existing state
        self.penalty = 0.1
        self.reward = 0.1

        # True if a snapshot is taken for the state
        self.solid = False

    def add_child(self, child_node):
        self.out_nodes[child_node.uid] = child_node

    #    self.adjust_fitness_score()

    def add_hit_count(self):
        self.hit_count = self.hit_count + 1

    def add_restore_count(self):
        self.restore_count = self.restore_count + 1

    def add_transitions_triggered(self):
        self.transitions_triggered = self.transitions_triggered + 1

    #   self.adjust_fitness_score()

    def set_controllable_widgets(self, nr_widgets):
        self.controllable_widgets = nr_widgets

    #    self.adjust_fitness_score()

    def add_event_sequence_to_transition(self, event_seq_len):
        self.number_events_to_transition.append(event_seq_len)
        # print self.number_events_to_transition
        # self.adjust_fitness_score()

    def get_average_events_to_transition(self):
        if len(self.number_events_to_transition) == 0:
            return 1

        return sum(self.number_events_to_transition) / len(self.number_events_to_transition)

    def add_transition_to_existing_state(self):
        self.num_transitions_to_existing_state = self.num_transitions_to_existing_state + 1
        # if self.num_transitions_to_existing_state > self.MAX_TRANSITIONS_TO_EXISTING_STATE:
        self.penalize_state()

    def add_transition_to_high_coverage(self, child):
        self.reward_state()
        child.reward_state()

    def add_transition_to_low_coverage(self, child):
        self.penalize_state()
        child.fitness_score = 0

    def add_transition_to_new_state(self):
        self.num_transitions_to_existing_state = 0
        self.reward_state()

    def penalize_state(self):
        print "===================> PENALIZE STATE <====================="
        self.num_transitions_to_existing_state = 0
        self.fitness_score = self.fitness_score - (self.fitness_score * self.penalty)
        if self.fitness_score < 0:
            self.fitness_score = 0

        # self.penalty = self.penalty + 0.2
        # self.reward = 0.1

        if self.penalty > 1.00:
            self.penalty = 1.00

    def reward_state(self):
        print "===================> REWARD STATE <====================="
        if self.fitness_score == 0:
            self.fitness_score = 20 + self.controllable_widgets
        else:
            self.fitness_score = self.fitness_score + (self.fitness_score * self.reward)

        if self.fitness_score > State.MAX_FITNESS:
            self.fitness_score = State.MAX_FITNESS

        # self.penalty = 0.1
        # self.reward = self.reward + self.reward
        if self.reward > 1.00:
            self.reward = 1.00

    # def adjust_fitness_score(self):
    #    if self.num_transitions_to_existing_state > State.MAX_TRANSITIONS_TO_EXISTING_STATE:
    #        self.penalize_state()
    #        self.num_transitions_to_existing_state = 0
    #    return self.fitness_score

    def __str__(self):
        formatted_output = 'key: ' + str(self.uid)
        formatted_output = formatted_output + ' snapshot: ' + str(self.solid)
        formatted_output = formatted_output + ' hit: ' + str(self.hit_count)
        formatted_output = formatted_output + ' restore: ' + str(self.restore_count)
        formatted_output = formatted_output + ' trans: ' + str(self.transitions_triggered)
        # formatted_output = formatted_output + ' widgets: ' + str(self.controllable_widgets)
        formatted_output = formatted_output + ' fitness: ' + str(self.fitness_score)
        formatted_output = formatted_output + ' old trans: ' + str(self.num_transitions_to_existing_state)
        # formatted_output = formatted_output + ' num_events: '
        for n in self.number_events_to_transition:
            formatted_output = formatted_output + str(n) + ','
        formatted_output = formatted_output + '\n'

        edge = ''
        for k in self.out_nodes:
            edge = edge + '{' + k + '} '
        formatted_output = formatted_output + 'edges: ' + edge

        return formatted_output


class StateGraph:
    states = {}  # used as static variable containing all states

    def is_exist(self, uid):
        """
        identify whether the state exists in the graph
        :param uid: uid of the state to be checked
        """

        if self.retrieve(uid) is None:
            return False
        else:
            return True

    def retrieve(self, uid):
        """
        retrieve the state in the graph with uid
        :param uid:
        :return:
        """

        if uid in StateGraph.states:
            return StateGraph.states[uid]
        else:
            return None

    def retrieve2(self, uid):
        """
        retrieve the state in the graph with uid
        :param uid:
        :return:
        """

        if uid in StateGraph.states:
            return [StateGraph.states[uid]]
        else:
            return None

    def add_node(self, state_id):
        if state_id in StateGraph.states:
            return
        new_state = State(state_id)
        StateGraph.states[state_id] = new_state

    def add_edge(self, parent_id, child_id):
        assert (parent_id != child_id), 'cannot add an edge to itself'

        parent = self.retrieve(parent_id)
        child = self.retrieve(child_id)

        assert (parent is not None), 'the parent state is not in the graph'
        assert (child is not None), 'the child state is not in the graph'

        parent.add_child(child)
        parent.add_transitions_triggered()

        child.add_hit_count()

    def dump(self):
        for key in StateGraph.states:
            print str(StateGraph.states[key])

    def get_nlargerest(self, p):
        frequent_nodes = heapq.nlargest(int(len(StateGraph.states) * p), StateGraph.states.items(),
                                        key=lambda x: x[1].hit_count)
        # print ([(x[0], x[1].transitions_triggered) for x in frequent_nodes])

        print "Frequent nodes "
        print frequent_nodes
        return frequent_nodes

    def get_least_fittest_nodes(self, p):
        frequent_nodes = heapq.nsmallest(int(math.ceil(len(StateGraph.states) * p)), StateGraph.states.items(),
                                         key=lambda x: x[1].fitness_score)

        print "Least interesting nodes: "
        print " p * num_states : " + str(math.ceil(len(StateGraph.states) * p))
        print frequent_nodes
        return frequent_nodes

    def get_nfittest(self, num_nodes):
        frequent_nodes = heapq.nlargest(num_nodes, StateGraph.states.items(), key=lambda x: x[1].fitness_score)

        print "Fittest nodes "
        print frequent_nodes
        return frequent_nodes

    # def get_least_visit_node(self):
    #    return min(StateGraph.states.items(), key=lambda x: (x[1].hit_count + x[1].restore_count))[1]

    def get_fittest_state(self, strategy):
        # print "Debug:" + str(strategy) + str(type(strategy))
        if strategy == 0:
            print "using fittest score ..."
            return max(filter(lambda x: x[1].solid, StateGraph.states.items()), key=lambda x: x[1].fitness_score)[1]
        if strategy == 1:
            print " using hit_count ..."
            return min(filter(lambda x: x[1].solid, StateGraph.states.items()),
                       key=lambda x: (x[1].hit_count + x[1].restore_count))[1]

    def get_avg_fitness(self):
        if len(StateGraph.states.items()) == 0:
            return 0

        sa = 0
        for st in StateGraph.states.items():
            sa = sa + st[1].fitness_score

        return sa / len(StateGraph.states)

    def compute_frequent_node_portion(self, nodes, top_portion):
        if len(set(nodes)) == 0:
            return 0
        return float(len([True for x in Counter([x[0] for x in self.get_nlargerest(top_portion)] + nodes).items() if
                          x[1] >= 2])) / float(len(set(nodes)))

    def compute_least_fittest_nodes_portion(self, recent_states, top_portion):
        num = 0
        unfit_states = self.get_least_fittest_nodes(top_portion)

        print "unfit_states::::: " + str(unfit_states)

        if len(set(recent_states)) == 0:
            return 0

        for state in recent_states:
            print "recent::::" + str(state)
            for element in unfit_states:
                if state == element[0]:
                    num = num + 1
                    print "comming in..."
            print "num :: " + str(num) + "  :: " + str(len(recent_states)) + " portion: " + str(
                float(float(num) / float(len(recent_states))))
        return float(float(num) / float(len(recent_states)))

        # return float(len([True for x in Counter([x[0] for x in self.get_least_fittest_nodes(top_portion)] + nodes).items() if x[1] >= 2])) / float(len(set(nodes)))

    @staticmethod
    def get_total_transitions():
        return sum(map(lambda x: (x[1].hit_count + x[1].restore_count), StateGraph.states.items()))

    def get_snapshots(self):
        return filter(lambda x: x[1].solid, StateGraph.states.items())


if __name__ == '__main__':

    state_graph = StateGraph()
    state_graph.add_root('1')
    state_graph.add_node('1', '2')
    state = state_graph.retrieve2('1')[0]
    state.add_transitions_triggered()
    state.add_transitions_triggered()

    state_graph.add_node('1', '3')
    state_graph.add_node('2', '4')
    state_graph.add_node('4', '6')
    state_graph.add_node('6', '7')

    s = state_graph.get_least_visit_node()
    print s.transitions_triggered
    print s.uid
    state_graph.get_nlargerest(0.7)

    p = ['1', '3', '4']
    # print state_graph.compute_portion(p,0.7)

    state_graph.dump()
