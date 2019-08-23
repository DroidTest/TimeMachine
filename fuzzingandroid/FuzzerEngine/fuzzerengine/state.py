#!/usr/bin/python

# class Event:
#   def __init__(self, event):
#     self.event = event
#
# class Edge:
#   def __init__(self, events, state):
#     self.events = events
#     self.state = state

class State:
  states    = {}      # Used as static variable containing all states

  uid       = 0       # Unique ID representing this state
  ancestors = 0       # Length of creation path from root
  out_nodes = {}      # Direct, reachable successor states
  hit_count = 1       # Number of times this state was reached
  sel_count = 0       # Number of times this state was selected
  avg_event_seq = 0   # average(number of events which trigger a transition to another state)

  def __init__(self, uid, ancestors):
    self.uid = uid
    self.ancestors = ancestors
    assert (uid not in State.states)
    State.states[uid] = self

  def reachedState(self, events, uid):

    if uid == self.uid: return

    # If edge exists, increase hit_count and minimize input sequence
    if uid in self.out_edges:
      self.out_edges[uid].state.hit_count += 1

      if len(events) < len(self.out_edges[uid].events):
        self.out_edges[uid].events = events

    # Else if state has already been constructed, reuse it
    elif uid in State.states:

      successor = State.states[uid]
      if successor.ancestors > self.ancestors + 1:
        successor.ancestors = self.ancestors + 1
        # TODO Recursively update ancestors. Watch out for cycles!

      self.out_edges[uid] = Edge(events, successor)

    # Else construct new state
    else:
      self.out_edges[uid] = Edge(events, State(uid, self.ancestors + 1))



# Test it
root = State(1, 0)
e1 = Event("Click [0.1,0.34]")
e2 = Event("Text input [Hi]")

state2_uid = 2
root.reachedState([e1,e2], state2_uid)
assert 2 == len(root.out_edges[state2_uid].events)

root.reachedState([e1], state2_uid)
assert 2 == root.out_edges[state2_uid].state.hit_count
assert 1 == len(root.out_edges[state2_uid].events)
