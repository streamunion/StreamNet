package com.iota.iri.storage;

import com.iota.iri.conf.BaseIotaConfig;
import com.iota.iri.model.Hash;
import com.iota.iri.storage.localinmemorygraph.LocalInMemoryGraphProvider;
import com.iota.iri.model.StateDiff;
import com.iota.iri.model.persistables.*;
import com.iota.iri.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import java.util.Set;


/**
 * Created by paul on 3/3/17 for iri.
 */
public class Tangle {
    private static final Logger log = LoggerFactory.getLogger(Tangle.class);

    // TODO: make 'txnCount' persistable.
    private static AtomicLong txnCount = new AtomicLong(0);;

    public void addTxnCount(long count) {
        txnCount.addAndGet(count);
    }

    public long getTxnCount() {
        return txnCount.get();
    }

    public static final Map<String, Class<? extends Persistable>> COLUMN_FAMILIES =
            new LinkedHashMap<String, Class<? extends Persistable>>() {{
                put("transaction", Transaction.class);
                put("milestone", Milestone.class);
                put("stateDiff", StateDiff.class);
                put("address", Address.class);
                put("approvee", Approvee.class);
                put("bundle", Bundle.class);
                put("obsoleteTag", ObsoleteTag.class);
                put("tag", Tag.class);
            }};

    public static final Map.Entry<String, Class<? extends Persistable>> METADATA_COLUMN_FAMILY =
            new AbstractMap.SimpleImmutableEntry<>("transaction-metadata", Transaction.class);

    private final List<PersistenceProvider> persistenceProviders = new ArrayList<>();


    public void addPersistenceProvider(PersistenceProvider provider) {
        this.persistenceProviders.add(provider);
    }

    public PersistenceProvider getPersistenceProvider(String provider) {
        if(provider.equals("LOCAL_GRAPH")) {
            for(PersistenceProvider prov : this.persistenceProviders) {
                if(prov.getClass().equals(LocalInMemoryGraphProvider.class)) {
                    return prov;
                }
            }
        }
        return null;
    }

    public void init() throws Exception {
        for(PersistenceProvider provider: this.persistenceProviders) {
            provider.init();
        }
    }


    public void shutdown() throws Exception {
        log.info("Shutting down Tangle Persistence Providers... ");
        this.persistenceProviders.forEach(PersistenceProvider::shutdown);
        this.persistenceProviders.clear();
    }

    public Persistable load(Class<?> model, Indexable index) throws Exception {
            Persistable out = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if((out = provider.get(model, index)) != null) {
                    break;
                }
            }
            return out;
    }

    public Boolean saveBatch(List<Pair<Indexable, Persistable>> models) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.saveBatch(models);
            } else {
                exists = provider.saveBatch(models);
            }
        }
        return exists;
    }
    public Boolean save(Persistable model, Indexable index) throws Exception {
            boolean exists = false;
            for(PersistenceProvider provider: persistenceProviders) {
                if(exists) {
                    provider.save(model, index);
                } else {
                   exists = provider.save(model, index);
                }
            }
            return exists;
    }

    public void deleteBatch(Collection<Pair<Indexable, ? extends Class<? extends Persistable>>> models) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.deleteBatch(models);
        }
    }

    public void delete(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: persistenceProviders) {
                provider.delete(model, index);
            }
    }

    public Pair<Indexable, Persistable> getLatest(Class<?> model, Class<?> index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if (latest == null) {
                    latest = provider.latest(model, index);
                }
            }
            return latest;
    }

    public Boolean update(Persistable model, Indexable index, String item) throws Exception {
            boolean success = false;
            for(PersistenceProvider provider: this.persistenceProviders) {
                if(success) {
                    provider.update(model, index, item);
                } else {
                    success = provider.update(model, index, item);
                }
            }
            return success;
    }

    public Set<Indexable> keysWithMissingReferences(Class<?> modelClass, Class<?> referencedClass) throws Exception {
            Set<Indexable> output = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                output = provider.keysWithMissingReferences(modelClass, referencedClass);
                if(output != null && output.size() > 0) {
                    break;
                }
            }
            return output;
    }

    public Set<Indexable> keysStartingWith(Class<?> modelClass, byte[] value) {
            Set<Indexable> output = null;
            for(PersistenceProvider provider: this.persistenceProviders) {
                output = provider.keysStartingWith(modelClass, value);
                if(output.size() != 0) {
                    break;
                }
            }
            return output;
    }

    public Boolean exists(Class<?> modelClass, Indexable hash) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.exists(modelClass, hash)) {
                    return true;
                }
            }
            return false;
    }

    public Boolean maybeHas(Class<?> model, Indexable index) throws Exception {
            for(PersistenceProvider provider: this.persistenceProviders) {
                if (provider.mayExist(model, index)) {
                    return true;
                }
            }
            return false;
    }

    public Long getCount(Class<?> modelClass) throws Exception {
        if (BaseIotaConfig.getInstance().isEnableBatchTxns()) {
            return getTxnCount();
        }

        long value = 0;
        for(PersistenceProvider provider: this.persistenceProviders) {
            if((value = provider.count(modelClass)) != 0) {
                break;
            }
        }

        return value;
    }

    public Persistable find(Class<?> model, byte[] key) throws Exception {
            Persistable out = null;
            for (PersistenceProvider provider : this.persistenceProviders) {
                if ((out = provider.seek(model, key)) != null) {
                    break;
                }
            }
            return out;
    }

    public Pair<Indexable, Persistable> next(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.next(model, index);
                }
            }
            return latest;
    }

    public Pair<Indexable, Persistable> previous(Class<?> model, Indexable index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.previous(model, index);
                }
            }
            return latest;
    }

    public Pair<Indexable, Persistable > getFirst(Class<?> model, Class<?> index) throws Exception {
            Pair<Indexable, Persistable> latest = null;
            for(PersistenceProvider provider: persistenceProviders) {
                if(latest == null) {
                    latest = provider.first(model, index);
                }
            }
            return latest;
    }

    public void clearColumn(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clear(column);
        }
    }

    public void clearMetadata(Class<?> column) throws Exception {
        for(PersistenceProvider provider: persistenceProviders) {
            provider.clearMetadata(column);
        }
    }

    public Hash getMaxScoreHashOnLevel(int depth){
        for(PersistenceProvider provider : persistenceProviders) {
            if (provider instanceof  LocalInMemoryGraphProvider) {
                return provider.getPivotalHash(depth);
            }
        }
        return null;
    }

    public Hash getLastPivot(){
        for(PersistenceProvider provider : persistenceProviders){
            if (provider instanceof  LocalInMemoryGraphProvider){
                Hash genesis = ((LocalInMemoryGraphProvider)provider).getGenesis();
                return ((LocalInMemoryGraphProvider)provider).getPivot(genesis);
            }
        }
        return null;
    }

    public void buildGraph(){
        for(PersistenceProvider provider : persistenceProviders){
            if (provider instanceof  LocalInMemoryGraphProvider){
                provider.buildGraph();
            }
        }
    }

    public void computeScore(){
        for(PersistenceProvider provider : persistenceProviders){
            if (provider instanceof  LocalInMemoryGraphProvider){
                provider.computeScore();
            }
        }
    }

    public int getNumOfTips() {
        for(PersistenceProvider provider : persistenceProviders){
            if (provider instanceof  LocalInMemoryGraphProvider){
                return provider.getNumOfTips();
            }
        }
        return -1;
    }
    /*
    public boolean merge(Persistable model, Indexable index) throws Exception {
        boolean exists = false;
        for(PersistenceProvider provider: persistenceProviders) {
            if(exists) {
                provider.save(model, index);
            } else {
                exists = provider.merge(model, index);
            }
        }
        return exists;
    }
    */
}
