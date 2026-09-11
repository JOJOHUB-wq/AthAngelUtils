import numpy as np, json
from sklearn.neural_network import MLPClassifier
from sklearn.model_selection import train_test_split
heads = []
for i in range(5):
    X = np.load(f'capwork/Xh{i}.npy'); y = np.load(f'capwork/Yh{i}.npy')
    Xtr,Xte,ytr,yte = train_test_split(X,y,test_size=0.15,random_state=3,stratify=y)
    clf = MLPClassifier(hidden_layer_sizes=(96,), activation='relu', solver='adam',
                        alpha=1e-4, batch_size=256, max_iter=50, random_state=7)
    clf.fit(Xtr,ytr)
    a = clf.score(Xte,yte)
    print(f'head {i}: holdout={a:.4f} iters={clf.n_iter_}', flush=True)
    heads.append(clf)
# full-captcha accuracy on holdout subset
Xt = [np.load(f'capwork/Xh{i}.npy') for i in range(5)]
Yt = [np.load(f'capwork/Yh{i}.npy') for i in range(5)]
n = len(Yt[0]); idx = np.random.RandomState(0).choice(n, 1500, replace=False)
ok = 0
for k in idx:
    if all(heads[i].predict(Xt[i][k:k+1])[0] == Yt[i][k] for i in range(5)):
        ok += 1
print('full-captcha acc:', ok/len(idx), flush=True)
CHARS='0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ'
def rnd(a):
    if isinstance(a, list): return [rnd(x) for x in a]
    return round(float(a), 3)
model = {'classes': CHARS, 'heads': []}
for clf in heads:
    model['heads'].append({'w1': rnd(clf.coefs_[0].tolist()), 'b1': rnd(clf.intercepts_[0].tolist()),
                           'w2': rnd(clf.coefs_[1].tolist()), 'b2': rnd(clf.intercepts_[1].tolist())})
open('capwork/fullmlp.json','w').write(json.dumps(model, separators=(',',':')))
import os; print('bytes:', os.path.getsize('capwork/fullmlp.json'), flush=True)
